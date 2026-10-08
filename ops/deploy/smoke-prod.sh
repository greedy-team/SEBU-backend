#!/usr/bin/env bash
# Isolated CI/local smoke test. Never connects to the deployed MySQL database.
set -euo pipefail
image=${1:?Usage: bash ops/deploy/smoke-prod.sh IMAGE}
script_dir=$(cd -- "$(dirname -- "${BASH_SOURCE[0]}")" && pwd)
repo_root=$(cd -- "$script_dir/../.." && pwd)
umask 077
scratch=$(mktemp -d)
network="sebu-ci-${RANDOM}-$$"
mysql_id=''
app_id=''
cleanup() {
  [[ -z "$app_id" ]] || docker rm --force "$app_id" >/dev/null 2>&1 || true
  # -v removes only this disposable container's anonymous MySQL volume.
  [[ -z "$mysql_id" ]] || docker rm --force --volumes "$mysql_id" >/dev/null 2>&1 || true
  docker network rm "$network" >/dev/null 2>&1 || true
  rm -f -- "$scratch/mysql.env" "$scratch/app.env" "$scratch/health.json" \
    "$scratch/response.json" "$scratch/metrics.prom" "$scratch/seed-report.tsv" "$scratch/cors.headers"
  rmdir -- "$scratch"
}
trap cleanup EXIT
db_password=$(openssl rand -hex 24)
monitoring_token=$(openssl rand -base64 32)
printf 'MYSQL_ROOT_PASSWORD=%s\nMYSQL_DATABASE=sebu\n' "$db_password" > "$scratch/mysql.env"
printf 'SPRING_PROFILES_ACTIVE=prod,monitoring\nDB_URL=jdbc:mysql://sebu-ci-mysql:3306/sebu?allowPublicKeyRetrieval=true&useSSL=false&serverTimezone=Asia/Seoul\nDB_USERNAME=root\nDB_PASSWORD=%s\nJWT_SECRET_BASE64=%s\nMONITORING_TOKEN=%s\nAPP_AUTH_CSRF_ALLOWEDORIGINS=https://sebu.kr,https://www.sebu.kr\nJAVA_TOOL_OPTIONS=-Xms128m -Xmx384m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=64m\n' \
  "$db_password" "$(openssl rand -base64 32)" "$monitoring_token" > "$scratch/app.env"
unset db_password
docker network create "$network" >/dev/null
mysql_id=$(docker run --detach --network "$network" --network-alias sebu-ci-mysql \
  --env-file "$scratch/mysql.env" mysql:8.0)
ready=false
for ((attempt=1; attempt<=90; attempt++)); do
  if docker exec "$mysql_id" sh -ec 'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot -h127.0.0.1 -e "SELECT 1"' >/dev/null 2>&1; then
    ready=true
    break
  fi
  sleep 2
done
[[ "$ready" == true ]] || { echo 'Disposable MySQL did not become ready'; exit 1; }
app_id=$(docker run --detach --network "$network" --publish 127.0.0.1::8080 \
  --memory 768m --env-file "$scratch/app.env" "$image")
address=$(docker port "$app_id" 8080/tcp)
for ((attempt=1; attempt<=120; attempt++)); do
  if [[ "$(docker inspect --format '{{.State.Running}}' "$app_id")" != true ]]; then
    echo 'Production smoke container exited. Inspect this isolated CI run.'
    docker logs --tail 100 "$app_id"
    exit 1
  fi
  status=$(curl --silent --max-time 5 --output "$scratch/response.json" --write-out '%{http_code}' \
    -H 'X-Forwarded-Proto: https' "http://$address/api/v1/laboratories" || true)
  health_status=$(curl --silent --max-time 5 --output "$scratch/health.json" --write-out '%{http_code}' \
    -H 'X-Forwarded-Proto: https' "http://$address/actuator/health/readiness" || true)
  if [[ "$status" == 200 && "$health_status" == 200 ]] \
      && python3 -c 'import json,sys; sys.exit(0 if json.load(open(sys.argv[1])).get("success") is True else 1)' "$scratch/response.json" \
      && python3 -c 'import json,sys; sys.exit(0 if json.load(open(sys.argv[1])) == {"status": "UP"} else 1)' "$scratch/health.json"; then
    unauthorized=$(curl --silent --max-time 5 --output /dev/null --write-out '%{http_code}' \
      -H 'X-Forwarded-Proto: https' "http://$address/actuator/prometheus" || true)
    authorized=$(curl --silent --max-time 5 --output "$scratch/metrics.prom" --write-out '%{http_code}' \
      -H 'X-Forwarded-Proto: https' -H "Authorization: Bearer $monitoring_token" \
      "http://$address/actuator/prometheus" || true)
    [[ "$unauthorized" == 401 && "$authorized" == 200 ]] || {
      echo 'Monitoring endpoint authentication failed'; exit 1;
    }
    grep -q 'http_server_requests_seconds_count' "$scratch/metrics.prom" || {
      echo 'Monitoring endpoint did not export HTTP metrics'; exit 1;
    }
    for origin in https://sebu.kr https://www.sebu.kr; do
      cors_status=$(curl --silent --max-time 5 --output /dev/null --dump-header "$scratch/cors.headers" \
        --write-out '%{http_code}' --request OPTIONS -H 'X-Forwarded-Proto: https' \
        -H "Origin: $origin" -H 'Access-Control-Request-Method: POST' \
        -H 'Access-Control-Request-Headers: Content-Type,X-XSRF-TOKEN' \
        "http://$address/api/v1/auth/login" || true)
      [[ "$cors_status" == 200 ]] \
        && tr -d '\r' < "$scratch/cors.headers" | grep -Fqix "Access-Control-Allow-Origin: $origin" \
        && tr -d '\r' < "$scratch/cors.headers" | grep -Fqix 'Access-Control-Allow-Credentials: true' || {
          echo "Production CORS did not allow configured origin: $origin"; exit 1;
        }
    done
    blocked_cors_status=$(curl --silent --max-time 5 --output /dev/null --write-out '%{http_code}' \
      --request OPTIONS -H 'X-Forwarded-Proto: https' -H 'Origin: https://sebu-frontend.vercel.app' \
      -H 'Access-Control-Request-Method: POST' "http://$address/api/v1/auth/login" || true)
    [[ "$blocked_cors_status" == 403 ]] || {
      echo 'Production CORS still accepts the development origin'; exit 1;
    }
    echo 'PASS: production origin override allows sebu.kr/www.sebu.kr and rejects the development origin'
    docker exec --interactive "$mysql_id" sh -ec \
      'MYSQL_PWD="$MYSQL_ROOT_PASSWORD" mysql -uroot --batch --raw --skip-column-names --default-character-set=utf8mb4 sebu' \
      < "$script_dir/verify-production-seed.sql" > "$scratch/seed-report.tsv"
    python3 - "$scratch/seed-report.tsv" "$repo_root" <<'PY'
from pathlib import Path
import sys

report, repository = map(Path, sys.argv[1:])
expected_versions = {
    file.name.split("__", 1)[0][1:].replace("_", ".")
    for directory, suffix in (("src/main/resources/db/migration", "sql"),
                              ("src/main/java/db/migration", "java"))
    for file in (repository / directory).glob(f"V*__*.{suffix}")
}
applied_versions = []
failures = []
for line in report.read_text(encoding="utf-8").splitlines():
    kind, label, value = line.split("\t", 2)
    if kind == "migration":
        applied_versions.append(label)
        if value != "1":
            failures.append(f"Migration V{label} did not succeed")
        continue
    print(f"{kind}: {label} = {value}")
    if kind == "zero" and int(value) != 0:
        failures.append(f"{label} must be zero, got {value}")
    elif kind == "nonempty" and int(value) <= 0:
        failures.append(f"Missing required deployment data: {label}")
if not expected_versions or set(applied_versions) != expected_versions or len(applied_versions) != len(expected_versions):
    failures.append(f"Migration mismatch: missing={sorted(expected_versions - set(applied_versions))}, "
                    f"unexpected={sorted(set(applied_versions) - expected_versions)}")
print(f"Flyway: applied={len(applied_versions)}, latest=V{applied_versions[-1] if applied_versions else 'NONE'}")
if failures:
    raise SystemExit("FAIL: fresh production database validation\n" + "\n".join(failures))
print("PASS: all source migrations applied; catalogues present; user activity and candidate tables empty")
PY
    echo 'PASS: MySQL 8.0 + full migrations + production seeds + Hibernate validation + monitoring + readiness + representative API'
    exit 0
  fi
  sleep 2
done
docker logs --tail 100 "$app_id"
echo 'Production API smoke test timed out'
exit 1
