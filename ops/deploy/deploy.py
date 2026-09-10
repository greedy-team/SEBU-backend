#!/usr/bin/env python3
"""Fail-closed, single-container pull deployment. No third-party Python packages.

This is deliberately NOT blue/green. MySQL and Caddy are never replaced.
Only trusted, CI-published develop images are eligible. Authentication is supplied
by the root user's Docker credential store, never by a token in this file.
"""

import argparse
import contextlib
import datetime
import gzip
import hashlib
import json
import os
from pathlib import Path
import re
import shutil
import subprocess
import sys
import tempfile
import time
import urllib.request
import zipfile


DIGEST = re.compile(r"sha256:[0-9a-f]{64}\Z")
SHA = re.compile(r"[0-9a-f]{40}\Z")
REQUIRED_ENV = ("DB_URL", "DB_USERNAME", "DB_PASSWORD", "JWT_SECRET_BASE64")
MYSQL_AUTH = '''
set -eu
if [ -n "${MYSQL_ROOT_PASSWORD_FILE:-}" ]; then
  MYSQL_PWD="$(cat "$MYSQL_ROOT_PASSWORD_FILE")"
else
  MYSQL_PWD="${MYSQL_ROOT_PASSWORD:?MYSQL_ROOT_PASSWORD is required}"
fi
export MYSQL_PWD
'''


class DeployError(RuntimeError):
    pass


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


def log(message):
    print(f"{datetime.datetime.now(datetime.timezone.utc).isoformat()} {message}", flush=True)


def fingerprint(items):
    """Hash relative SQL names and contents, identically from source and bootJar."""
    digest = hashlib.sha256()
    for name, data in sorted(items):
        digest.update(name.encode() + b"\0" + data + b"\0")
    return digest.hexdigest()


def source_fingerprint(root):
    root = Path(root)
    items = [(p.relative_to(root).as_posix(), p.read_bytes()) for p in root.rglob("*.sql")]
    if not items:
        raise DeployError("No production Flyway SQL found")
    return fingerprint(items)


def jar_fingerprint(path):
    prefix = "BOOT-INF/classes/db/migration/"
    with zipfile.ZipFile(path) as archive:
        items = [(n[len(prefix):], archive.read(n)) for n in archive.namelist()
                 if n.startswith(prefix) and n.endswith(".sql")]
    if not items:
        raise DeployError("Cannot identify previous production migrations; stop for review")
    return fingerprint(items)


def atomic_json(path, value):
    path = Path(path)
    temporary = path.with_name(path.name + ".tmp")
    with temporary.open("w", encoding="utf-8") as output:
        json.dump(value, output, indent=2)
        output.write("\n")
        output.flush()
        os.fsync(output.fileno())
    os.chmod(temporary, 0o600)
    os.replace(temporary, path)


def read_json(path, default=None):
    try:
        return json.loads(Path(path).read_text(encoding="utf-8"))
    except FileNotFoundError:
        return default


def private_file(path):
    path = Path(path)
    info = path.lstat()
    if path.is_symlink() or not path.is_file() or info.st_uid != 0 or info.st_mode & 0o077:
        raise DeployError(f"Must be a root-owned, non-symlink mode-0600 file: {path}")


def load_config(path):
    private_file(path)
    config = read_json(path)
    if not re.fullmatch(r"ghcr\.io/[a-z0-9_.-]+/[a-z0-9_.-]+", config["image"]):
        raise DeployError("Only an explicit GHCR image repository is supported")
    for key in ("container", "mysql_container", "mysql_volume", "network", "database"):
        if not re.fullmatch(r"[A-Za-z0-9][A-Za-z0-9_.-]*", config[key]):
            raise DeployError(f"Invalid configuration field: {key}")
    if config["tag"] != "develop":
        raise DeployError("This deployment channel only accepts the develop tag")
    if not config["source"].startswith("https://github.com/"):
        raise DeployError("Expected a GitHub source repository")
    if not config["public_health_url"].startswith("https://"):
        raise DeployError("Public health check must use HTTPS")
    for key in ("memory_mb", "port", "health_timeout", "min_free_mb"):
        if not isinstance(config[key], int) or config[key] <= 0:
            raise DeployError(f"Expected a positive integer: {key}")
    for key in ("env_file", "state_dir"):
        if not Path(config[key]).is_absolute() or ".." in Path(config[key]).parts:
            raise DeployError(f"Expected an absolute safe path: {key}")
    return config


class Docker:
    def run(self, *args, timeout=120):
        try:
            result = subprocess.run(["docker", *args], text=True, capture_output=True,
                                    timeout=timeout, check=False)
        except (OSError, subprocess.TimeoutExpired) as error:
            raise DeployError(f"Docker {args[0]} unavailable or timed out") from error
        if result.returncode:
            # Do not echo inspect output, environment values, or credential diagnostics.
            raise DeployError(f"Docker {args[0]} failed (exit {result.returncode}); check locally")
        return result.stdout.strip()

    def inspect(self, name, image=False):
        args = ("image", "inspect", name) if image else ("inspect", name)
        return json.loads(self.run(*args))[0]

    def manifest(self, image, tag):
        result = json.loads(self.run("manifest", "inspect", "--verbose", f"{image}:{tag}"))
        manifests = result if isinstance(result, list) else [result]
        matches = []
        for item in manifests:
            descriptor = item.get("Descriptor", {})
            platform = descriptor.get("platform", {})
            if platform.get("os", "linux") == "linux" and platform.get("architecture", "amd64") == "amd64":
                candidate = descriptor.get("digest", "")
                if DIGEST.fullmatch(candidate):
                    matches.append(candidate)
        if len(matches) != 1:
            raise DeployError("Expected exactly one linux/amd64 deployment manifest")
        return matches[0]

    def backup(self, config, destination):
        raw = destination.with_suffix(".sql.partial")
        error_path = destination.with_suffix(".stderr")
        command = MYSQL_AUTH + '''exec mysqldump --user=root --single-transaction \
--routines --triggers --events --hex-blob --set-gtid-purged=OFF \
--no-tablespaces --databases "$1"'''
        raw_created = False
        try:
            with raw.open("xb") as output:
                raw_created = True
                os.chmod(raw, 0o600)
                with error_path.open("xb") as errors:
                    os.chmod(error_path, 0o600)
                    result = subprocess.run(["docker", "exec", config["mysql_container"],
                                             "sh", "-ec", command, "backup", config["database"]],
                                            stdout=output, stderr=errors, timeout=600, check=False)
            if result.returncode or raw.stat().st_size < 100:
                raise DeployError("MySQL backup failed; existing backend has not been stopped")
            with raw.open("rb") as source, gzip.open(destination, "xb") as compressed:
                shutil.copyfileobj(source, compressed)
            os.chmod(destination, 0o600)
            # Read the entire archive to verify gzip checksum before stopping the app.
            with gzip.open(destination, "rb") as source:
                while source.read(1024 * 1024):
                    pass
        finally:
            # Only remove the temporary raw dump created by this specific invocation.
            if raw_created:
                raw.unlink(missing_ok=True)


class Deployer:
    def __init__(self, config, docker=None):
        self.config = config
        self.docker = docker or Docker()
        self.state = Path(config["state_dir"])

    def preflight(self):
        c = self.config
        private_file(c["env_file"])
        env = parse_env(Path(c["env_file"]).read_text(encoding="utf-8"))
        validate_env(env)
        if shutil.disk_usage(self.state).free < c["min_free_mb"] * 1024 * 1024:
            raise DeployError("Insufficient disk space; remove only reviewed old images/backups")
        previous = self.docker.inspect(c["container"])
        live_env = parse_env("\n".join(previous["Config"]["Env"]))
        if live_env.get("DB_URL") != env["DB_URL"] or live_env.get("SPRING_PROFILES_ACTIVE") != "prod":
            raise DeployError("Saved DB target/profile differs from the existing backend; review before deployment")
        if not previous["State"]["Running"] or previous["State"].get("Health", {}).get("Status") != "healthy":
            raise DeployError("Existing backend is not running and healthy; manual inspection required")
        if previous.get("Mounts"):
            raise DeployError("Backend has mounts this installer cannot reproduce; review configuration")
        if set(previous["NetworkSettings"]["Networks"]) != {c["network"]}:
            raise DeployError("Backend network differs from the reviewed deployment configuration")
        ports = previous["HostConfig"]["PortBindings"]
        expected = {"8080/tcp": [{"HostIp": "127.0.0.1", "HostPort": str(c["port"])}]}
        if ports != expected or previous["HostConfig"]["Memory"] != c["memory_mb"] * 1024 * 1024:
            raise DeployError("Backend ports or memory limit differ; refusing to replace the container")
        mysql = self.docker.inspect(c["mysql_container"])
        if c["network"] not in mysql["NetworkSettings"]["Networks"]:
            raise DeployError("MySQL is not on the backend network")
        if not env["DB_URL"].startswith(f"jdbc:mysql://{c['mysql_container']}:3306/{c['database']}?"):
            if env["DB_URL"] != f"jdbc:mysql://{c['mysql_container']}:3306/{c['database']}":
                raise DeployError("DB_URL must identify the reviewed MySQL container/database")
        if not mysql["State"]["Running"] or not any(
            m.get("Type") == "volume" and m.get("Name") == c["mysql_volume"]
            and m.get("Destination") == "/var/lib/mysql" for m in mysql.get("Mounts", [])
        ):
            raise DeployError("MySQL must be running on the expected persistent volume")
        self.docker.run("network", "inspect", c["network"])
        return previous

    def previous_migrations(self):
        with tempfile.TemporaryDirectory(prefix="previous-jar-", dir=self.state) as directory:
            target = Path(directory) / "app.jar"
            self.docker.run("cp", f"{self.config['container']}:/app/app.jar", str(target))
            return jar_fingerprint(target)

    def health(self):
        c = self.config
        deadline = time.monotonic() + c["health_timeout"]
        while time.monotonic() < deadline:
            container = self.docker.inspect(c["container"])
            if not container["State"]["Running"]:
                return False
            if container["State"].get("Health", {}).get("Status") == "healthy":
                try:
                    urls = (f"http://127.0.0.1:{c['port']}/api/v1/laboratories", c["public_health_url"])
                    for url in urls:
                        request = urllib.request.Request(url, headers={"X-Forwarded-Proto": "https"})
                        with urllib.request.build_opener(NoRedirect).open(request, timeout=10) as response:
                            if response.status != 200 or json.load(response).get("success") is not True:
                                raise ValueError("API is not ready")
                    return True
                except (OSError, ValueError):
                    pass
            time.sleep(3)
        return False

    def deploy(self, expected_digest=None):
        c = self.config
        blocked = self.state / "blocked.json"
        progress = self.state / "in-progress.json"
        if blocked.exists() or progress.exists():
            raise DeployError("Deployment is blocked after failure/interruption; inspect status before recovery")
        target = self.docker.manifest(c["image"], c["tag"])
        if expected_digest and target != expected_digest:
            raise DeployError("Deployment target changed since review; run check again")
        current = read_json(self.state / "current.json", {})
        if current.get("digest") == target:
            live = self.docker.inspect(c["container"])
            if (live["Image"] != current.get("image_id") or not live["State"]["Running"]
                    or live["State"].get("Health", {}).get("Status") != "healthy"):
                raise DeployError("Recorded version does not match the running container; inspect manually")
            log("No new deployment image")
            return
        previous = self.preflight()
        ref = f"{c['image']}@{target}"
        self.docker.run("pull", ref, timeout=600)
        image = self.docker.inspect(ref, image=True)
        labels = image.get("Config", {}).get("Labels") or {}
        revision = labels.get("org.opencontainers.image.revision", "")
        migrations = labels.get("io.sebu.migrations-sha256", "")
        if labels.get("org.opencontainers.image.source") != c["source"] or not SHA.fullmatch(revision):
            raise DeployError("Image does not identify the configured repository and commit")
        if labels.get("io.sebu.deployment-channel") != "develop" or not re.fullmatch(r"[0-9a-f]{64}", migrations):
            raise DeployError("Image was not published with the required deployment metadata")
        if image.get("Os") != "linux" or image.get("Architecture") != "amd64":
            raise DeployError("Only linux/amd64 images are supported on this EC2 instance")
        old_migrations = self.previous_migrations()
        # A new version might have been published during the download. Never deploy an obsolete pointer.
        if self.docker.manifest(c["image"], c["tag"]) != target:
            log("Target advanced during preparation; leave current backend running and check next time")
            return
        stamp = datetime.datetime.now(datetime.timezone.utc).strftime("%Y%m%dT%H%M%S%fZ")
        rollback = f"{c['container']}-rollback-{stamp}"
        backup_dir = self.state / "backups"
        backup_dir.mkdir(mode=0o700, exist_ok=True)
        backup = backup_dir / f"{stamp}-{revision[:12]}.sql.gz"
        record = {"digest": target, "revision": revision, "previous_image_id": previous["Image"],
                  "rollback_container": rollback, "backup": str(backup), "stage": "backup",
                  "same_migrations": old_migrations == migrations}
        atomic_json(progress, record)
        stopped = renamed = started = False
        try:
            self.docker.backup(c, backup)
            if shutil.disk_usage(self.state).free < c["min_free_mb"] * 1024 * 1024:
                raise DeployError("Insufficient disk space after backup; backend remains unchanged")
            if self.docker.manifest(c["image"], c["tag"]) != target:
                progress.unlink()
                log("Target advanced during backup; current backend left running")
                return
            record["stage"] = "replace-backend"
            atomic_json(progress, record)
            self.docker.run("stop", "--time", "60", c["container"])
            stopped = True
            self.docker.run("rename", c["container"], rollback)
            renamed = True
            self.docker.run("run", "--detach", "--name", c["container"],
                            "--restart", "no", "--network", c["network"],
                            "--publish", f"127.0.0.1:{c['port']}:8080",
                            "--memory", f"{c['memory_mb']}m", "--env-file", c["env_file"],
                            "--log-driver", "json-file", "--log-opt", "max-size=10m",
                            "--log-opt", "max-file=3", "--label", "io.sebu.pull-deploy=true", ref)
            started = True
            record["stage"] = "healthcheck"
            atomic_json(progress, record)
            if not self.health():
                raise DeployError("New backend failed health checks")
            self.docker.run("update", "--restart", "unless-stopped", c["container"])
            atomic_json(self.state / "current.json", {
                "digest": target, "revision": revision, "image_id": image["Id"],
                "migrations": migrations, "deployed_at": stamp, "rollback_container": rollback,
                "backup": str(backup)
            })
            progress.unlink()
            log(f"Deployment succeeded: {revision} ({target})")
        except Exception as error:
            record["stage_failed"] = record["stage"]
            record["error"] = type(error).__name__  # exception text can include sensitive output
            record["rollback"] = "not-attempted"
            # A docker run timeout may still have created a container. Acknowledgement is required
            # in all failure cases; never let the timer retry partially applied DB changes.
            try:
                candidate = None
                if renamed:
                    try:
                        candidate = self.docker.inspect(c["container"])
                    except DeployError:
                        pass
                    if candidate:
                        owned = (candidate.get("Config", {}).get("Labels") or {}).get("io.sebu.pull-deploy")
                        if candidate["Image"] != image["Id"] or owned != "true":
                            raise DeployError("Unexpected replacement container; manual recovery required")
                        # Keep a failed candidate stopped, including after a post-health failure.
                        self.docker.run("update", "--restart", "no", c["container"])
                        self.docker.run("stop", "--time", "30", c["container"])
                if stopped and (not started or record["same_migrations"]):
                    if renamed:
                        if candidate:
                            if not record["same_migrations"]:
                                raise DeployError("Candidate may have started migration; manual recovery required")
                            self.docker.run("rm", "--force", c["container"])
                        self.docker.run("rename", rollback, c["container"])
                    self.docker.run("start", c["container"])
                    record["rollback"] = "healthy" if self.health() else "unhealthy"
                elif stopped:
                    record["rollback"] = "manual-db-review-required"
            except Exception:
                record["rollback"] = "manual-review-required"
            atomic_json(blocked, record)
            progress.unlink(missing_ok=True)
            raise DeployError(f"Deployment halted; inspect {blocked}. No DB rollback/repair was performed") from error


def parse_env(text):
    result = {}
    for line in text.splitlines():
        if not line or line.startswith("#"):
            continue
        if "=" not in line:
            raise DeployError("Environment file must use literal NAME=value lines")
        key, value = line.split("=", 1)
        if not re.fullmatch(r"[A-Za-z_][A-Za-z0-9_]*", key) or key in result:
            raise DeployError("Environment contains an invalid or duplicate variable name")
        result[key] = value
    return result


def validate_env(env):
    if env.get("SPRING_PROFILES_ACTIVE") != "prod":
        raise DeployError("Only the prod profile is permitted; never deploy local seed data")
    if any(not env.get(key) for key in REQUIRED_ENV):
        raise DeployError("Missing required DB/JWT environment values (values are not logged)")
    if env.get("SPRING_FLYWAY_ENABLED", "true").lower() != "true":
        raise DeployError("Flyway must remain enabled")
    if env.get("SPRING_JPA_HIBERNATE_DDL_AUTO", "validate") != "validate":
        raise DeployError("Hibernate ddl-auto must remain validate")


def capture_env(config, docker):
    destination = Path(config["env_file"])
    if destination.exists():
        raise DeployError("Environment file already exists; it will not be overwritten")
    previous = docker.inspect(config["container"])
    base_vars = {"PATH", "JAVA_HOME", "JAVA_VERSION", "LANG", "LANGUAGE", "LC_ALL"}
    lines = []
    for entry in previous["Config"]["Env"]:
        if any(char in entry for char in ("\n", "\r", "\0")):
            raise DeployError("Multiline environment value requires manual setup")
        if entry.partition("=")[0] not in base_vars:
            lines.append(entry)
    validate_env(parse_env("\n".join(lines)))
    with destination.open("x", encoding="utf-8") as output:
        os.chmod(destination, 0o600)
        output.write("\n".join(lines) + "\n")
    log("Existing backend environment saved privately; no values printed")


@contextlib.contextmanager
def deploy_lock(state):
    import fcntl
    with (state / "deploy.lock").open("a") as lock:
        try:
            fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError as error:
            raise DeployError("Another deployment is in progress") from error
        yield


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("command", choices=("check", "deploy", "status", "capture-env", "fingerprint", "acknowledge-failure"))
    parser.add_argument("--config", default="/etc/sebu-deploy/config.json")
    parser.add_argument("--expected-digest")
    parser.add_argument("--sql-dir", default="src/main/resources/db/migration")
    args = parser.parse_args()
    if args.command == "fingerprint":
        print(source_fingerprint(args.sql_dir))
        return
    if os.geteuid() != 0:
        raise DeployError("Run with sudo; Docker credentials and runtime secrets are root-owned")
    os.umask(0o077)
    config = load_config(args.config)
    state = Path(config["state_dir"])
    if state.is_symlink() or not state.is_dir() or state.stat().st_uid != 0 or state.stat().st_mode & 0o022:
        raise DeployError("State directory must be an existing root-owned protected directory")
    if args.expected_digest and not DIGEST.fullmatch(args.expected_digest):
        raise DeployError("Expected a complete sha256 image digest")
    deployer = Deployer(config)
    with deploy_lock(state):
        if args.command == "status":
            for filename in ("current.json", "blocked.json", "in-progress.json"):
                print(json.dumps({filename: read_json(state / filename)}, indent=2))
        elif args.command == "capture-env":
            capture_env(config, deployer.docker)
        elif args.command == "check":
            deployer.preflight()
            digest = deployer.docker.manifest(config["image"], config["tag"])
            print(json.dumps({"target": f"{config['image']}@{digest}", "digest": digest,
                              "current": read_json(state / "current.json"),
                              "blocked": (state / "blocked.json").exists() or (state / "in-progress.json").exists()}, indent=2))
        elif args.command == "acknowledge-failure":
            markers = [state / n for n in ("blocked.json", "in-progress.json") if (state / n).exists()]
            if not args.expected_digest or not markers:
                raise DeployError("Review DB/container state first, then specify the blocked --expected-digest")
            if any(read_json(p).get("digest") != args.expected_digest for p in markers):
                raise DeployError("Digest does not match the blocked deployment")
            archive = state / "reviewed-failures"
            archive.mkdir(mode=0o700, exist_ok=True)
            for marker in markers:
                marker.rename(archive / f"{time.time_ns()}-{marker.name}")
            log("Failure acknowledged; no container or database was changed")
        else:
            deployer.deploy(args.expected_digest)


if __name__ == "__main__":
    try:
        main()
    except (DeployError, OSError, ValueError, KeyError, subprocess.TimeoutExpired) as error:
        # Avoid leaking configuration values through arbitrary exception representations.
        print(str(error) if isinstance(error, DeployError) else f"Deployment check failed: {type(error).__name__}", file=sys.stderr)
        sys.exit(1)
