import copy
import gzip
import importlib.util
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import Mock, patch
import zipfile

MODULE = Path(__file__).resolve().parents[1] / 'deploy.py'
spec = importlib.util.spec_from_file_location('sebu_deploy', MODULE)
d = importlib.util.module_from_spec(spec)
spec.loader.exec_module(d)

DIGEST = 'sha256:' + 'a' * 64
NEW_DIGEST = 'sha256:' + 'b' * 64
REVISION = 'c' * 40
MIGRATIONS = 'd' * 64
ENV = ('SPRING_PROFILES_ACTIVE=prod\nDB_URL=jdbc:mysql://sebu-mysql:3306/sebu\n'
       'DB_USERNAME=app\nDB_PASSWORD=fake-only-$#=password\nJWT_SECRET_BASE64=fake-only\n')


class FakeDocker:
    def __init__(self, config):
        self.config = config
        self.calls = []
        self.targets = [DIGEST]
        self.backup_error = False
        self.run_error_after_create = False
        self.candidate = {
            'Id': 'sha256:new-image', 'Os': 'linux', 'Architecture': 'amd64',
            'Config': {'Labels': {
                'org.opencontainers.image.source': config['source'],
                'org.opencontainers.image.revision': REVISION,
                'io.sebu.deployment-channel': 'develop',
                'io.sebu.migrations-sha256': MIGRATIONS}}}
        self.containers = {
            'sebu-backend': {
                'Image': 'sha256:old-image', 'State': {'Running': True, 'Health': {'Status': 'healthy'}},
                'Mounts': [], 'Config': {'Env': ENV.strip().splitlines()},
                'NetworkSettings': {'Networks': {'sebu-network': {}}},
                'HostConfig': {'Memory': 768 * 1024 * 1024,
                               'PortBindings': {'8080/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '8080'}]}}},
            'sebu-mysql': {
                'State': {'Running': True}, 'NetworkSettings': {'Networks': {'sebu-network': {}}},
                'Mounts': [{'Type': 'volume', 'Name': 'sebu-mysql-data', 'Destination': '/var/lib/mysql'}]}}

    def inspect(self, name, image=False):
        if image:
            return copy.deepcopy(self.candidate)
        if name not in self.containers:
            raise d.DeployError('Container not found')
        return copy.deepcopy(self.containers[name])

    def manifest(self, image, tag):
        self.calls.append(('manifest', image, tag))
        return self.targets.pop(0) if len(self.targets) > 1 else self.targets[0]

    def backup(self, config, destination):
        self.calls.append(('backup', str(destination)))
        if self.backup_error:
            raise d.DeployError('Simulated backup failure')
        destination.write_bytes(b'fake backup for unit tests only')

    def run(self, *args, **kwargs):
        self.calls.append(args)
        if args[0] == 'stop':
            self.containers[args[-1]]['State']['Running'] = False
        elif args[0] == 'rename':
            self.containers[args[2]] = self.containers.pop(args[1])
        elif args[0] == 'run':
            self.containers['sebu-backend'] = {
                'Image': self.candidate['Id'], 'State': {'Running': True, 'Health': {'Status': 'healthy'}},
                'Config': {'Labels': {'io.sebu.pull-deploy': 'true'}}}
            if self.run_error_after_create:
                raise d.DeployError('Simulated run timeout after create')
        elif args[0] == 'rm':
            del self.containers[args[-1]]
        elif args[0] == 'start':
            self.containers[args[-1]]['State']['Running'] = True
        return ''


class DeploymentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.addCleanup(self.temp.cleanup)
        self.state = Path(self.temp.name)
        self.env = self.state / 'backend.env'
        self.env.write_text(ENV, encoding='utf-8')
        self.config = json.loads((MODULE.parent / 'config.example.json').read_text())
        self.config.update(state_dir=str(self.state), env_file=str(self.env))
        self.docker = FakeDocker(self.config)
        self.deployer = d.Deployer(self.config, self.docker)
        self.deployer.previous_migrations = Mock(return_value=MIGRATIONS)
        self.deployer.health = Mock(return_value=True)
        for target, value in [('private_file', None), ('log', None)]:
            mock = patch.object(d, target, return_value=value)
            mock.start()
            self.addCleanup(mock.stop)
        space = patch.object(d.shutil, 'disk_usage', return_value=Mock(free=10 * 1024**3))
        space.start()
        self.addCleanup(space.stop)

    def assert_not_stopped(self):
        self.assertFalse(any(c[0] == 'stop' for c in self.docker.calls))
        self.assertTrue(self.docker.containers['sebu-backend']['State']['Running'])

    def test_success_pins_digest_and_preserves_mysql_and_previous_container(self):
        mysql = copy.deepcopy(self.docker.containers['sebu-mysql'])
        self.deployer.deploy(DIGEST)
        current = d.read_json(self.state / 'current.json')
        self.assertEqual(current['digest'], DIGEST)
        self.assertEqual(current['revision'], REVISION)
        self.assertEqual(self.docker.containers['sebu-mysql'], mysql)
        self.assertIn(current['rollback_container'], self.docker.containers)
        self.assertTrue(Path(current['backup']).exists())
        launch = next(c for c in self.docker.calls if c[0] == 'run')
        self.assertEqual(launch[-1], self.config['image'] + '@' + DIGEST)
        self.assertEqual(launch[launch.index('--restart') + 1], 'no')
        self.assertIn(('update', '--restart', 'unless-stopped', 'sebu-backend'), self.docker.calls)
        self.assertFalse((self.state / 'in-progress.json').exists())
        self.assertFalse((self.state / 'blocked.json').exists())

    def test_unchanged_image_is_not_restarted(self):
        d.atomic_json(self.state / 'current.json', {'digest': DIGEST, 'image_id': 'sha256:old-image'})
        self.deployer.deploy()
        self.assert_not_stopped()
        self.assertFalse(any(c[0] in ('pull', 'backup') for c in self.docker.calls))

    def test_unchanged_but_unhealthy_image_is_reported(self):
        d.atomic_json(self.state / 'current.json', {'digest': DIGEST, 'image_id': 'sha256:old-image'})
        self.docker.containers['sebu-backend']['State']['Health']['Status'] = 'unhealthy'
        with self.assertRaises(d.DeployError):
            self.deployer.deploy()

    def test_reviewed_digest_must_still_match(self):
        with self.assertRaises(d.DeployError):
            self.deployer.deploy(NEW_DIGEST)
        self.assert_not_stopped()

    def test_preexisting_block_or_interruption_prevents_any_docker_call(self):
        for filename in ('blocked.json', 'in-progress.json'):
            with self.subTest(filename=filename):
                marker = self.state / filename
                d.atomic_json(marker, {'digest': DIGEST})
                with self.assertRaises(d.DeployError):
                    self.deployer.deploy()
                self.assertEqual(self.docker.calls, [])
                marker.unlink()

    def test_backup_failure_blocks_without_stopping_current_backend(self):
        self.docker.backup_error = True
        with self.assertRaises(d.DeployError):
            self.deployer.deploy()
        self.assert_not_stopped()
        self.assertTrue((self.state / 'blocked.json').exists())

    def test_failed_health_with_same_migrations_restores_previous_backend(self):
        self.deployer.health.side_effect = [False, True]
        with self.assertRaises(d.DeployError):
            self.deployer.deploy()
        self.assertEqual(self.docker.containers['sebu-backend']['Image'], 'sha256:old-image')
        self.assertTrue(self.docker.containers['sebu-backend']['State']['Running'])
        self.assertEqual(d.read_json(self.state / 'blocked.json')['rollback'], 'healthy')

    def test_failed_health_with_new_migrations_stops_candidate_no_auto_db_restore(self):
        self.deployer.previous_migrations.return_value = 'e' * 64
        self.deployer.health.return_value = False
        with self.assertRaises(d.DeployError):
            self.deployer.deploy()
        self.assertFalse(self.docker.containers['sebu-backend']['State']['Running'])
        self.assertFalse(any(c[0] in ('rm', 'start') for c in self.docker.calls))
        self.assertEqual(d.read_json(self.state / 'blocked.json')['rollback'], 'manual-db-review-required')

    def test_run_timeout_with_new_migrations_stops_candidate_and_blocks(self):
        self.deployer.previous_migrations.return_value = 'e' * 64
        self.docker.run_error_after_create = True
        with self.assertRaises(d.DeployError):
            self.deployer.deploy()
        self.assertFalse(self.docker.containers['sebu-backend']['State']['Running'])
        self.assertFalse(any(c[0] in ('rm', 'start') for c in self.docker.calls))

    def test_run_timeout_with_same_migrations_restores_previous(self):
        self.docker.run_error_after_create = True
        with self.assertRaises(d.DeployError):
            self.deployer.deploy()
        self.assertEqual(self.docker.containers['sebu-backend']['Image'], 'sha256:old-image')

    def test_unexpected_candidate_is_never_removed(self):
        def foreign_candidate():
            self.docker.containers['sebu-backend']['Config']['Labels'] = {}
            return False
        self.deployer.health.side_effect = foreign_candidate
        with self.assertRaises(d.DeployError):
            self.deployer.deploy()
        self.assertFalse(any(c[0] == 'rm' for c in self.docker.calls))

    def test_stale_target_during_pull_or_backup_is_not_deployed(self):
        for targets in ([DIGEST, NEW_DIGEST], [DIGEST, DIGEST, NEW_DIGEST]):
            with self.subTest(targets=targets):
                self.docker.targets = list(targets)
                self.deployer.deploy()
                self.assert_not_stopped()
                self.assertFalse((self.state / 'in-progress.json').exists())

    def test_insufficient_disk_does_not_stop_backend(self):
        with patch.object(d.shutil, 'disk_usage', return_value=Mock(free=1)):
            with self.assertRaises(d.DeployError):
                self.deployer.deploy()
        self.assert_not_stopped()

    def test_missing_or_wrong_image_labels_never_stop_backend(self):
        for label in tuple(self.docker.candidate['Config']['Labels']):
            with self.subTest(label=label):
                saved = self.docker.candidate['Config']['Labels'].pop(label)
                with self.assertRaises(d.DeployError):
                    self.deployer.deploy()
                self.assert_not_stopped()
                self.docker.candidate['Config']['Labels'][label] = saved

    def test_drifted_mount_or_volume_or_ports_fail_closed(self):
        mutations = [
            lambda: self.docker.containers['sebu-backend'].update(Mounts=[{'Type': 'bind'}]),
            lambda: self.docker.containers['sebu-mysql']['Mounts'][0].update(Name='another-database'),
            lambda: self.docker.containers['sebu-backend']['HostConfig'].update(PortBindings={}),
        ]
        original = copy.deepcopy(self.docker.containers)
        for mutate in mutations:
            self.docker.containers = copy.deepcopy(original)
            mutate()
            with self.assertRaises(d.DeployError):
                self.deployer.deploy()
            self.assert_not_stopped()

    def test_wrong_db_destination_fails_before_backup(self):
        self.env.write_text(ENV.replace('sebu-mysql', 'unknown-host'))
        with self.assertRaises(d.DeployError):
            self.deployer.deploy()
        self.assert_not_stopped()

    def test_capture_keeps_literal_secret_characters_and_never_overwrites(self):
        self.env.unlink()
        d.capture_env(self.config, self.docker)
        self.assertEqual(d.parse_env(self.env.read_text())['DB_PASSWORD'], 'fake-only-$#=password')
        with self.assertRaises(d.DeployError):
            d.capture_env(self.config, self.docker)


class UtilityTests(unittest.TestCase):
    def test_backup_runs_dump_before_gzip_and_verifies_contents(self):
        payload = b'-- disposable test SQL\n' * 20
        def dump_process(command, **kwargs):
            self.assertNotIn('DB_PASSWORD', ' '.join(command))
            self.assertIn('mysqldump', command[5])
            kwargs['stdout'].write(payload)
            return Mock(returncode=0)
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'snapshot.sql.gz'
            with patch.object(d.subprocess, 'run', side_effect=dump_process):
                d.Docker().backup({'mysql_container': 'test-mysql', 'database': 'testdb'}, output)
            with gzip.open(output, 'rb') as source:
                self.assertEqual(source.read(), payload)
            self.assertFalse(output.with_suffix('.sql.partial').exists())

    def test_backup_failure_never_leaves_success_archive(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'snapshot.sql.gz'
            with patch.object(d.subprocess, 'run', return_value=Mock(returncode=1)):
                with self.assertRaises(d.DeployError):
                    d.Docker().backup({'mysql_container': 'test-mysql', 'database': 'testdb'}, output)
            self.assertFalse(output.exists())
            self.assertFalse(output.with_suffix('.sql.partial').exists())

    def test_backup_does_not_delete_preexisting_temporary_dump(self):
        with tempfile.TemporaryDirectory() as directory:
            output = Path(directory) / 'snapshot.sql.gz'
            previous = output.with_suffix('.sql.partial')
            previous.write_bytes(b'previous backup')
            with self.assertRaises(FileExistsError):
                d.Docker().backup({'mysql_container': 'test-mysql', 'database': 'testdb'}, output)
            self.assertEqual(previous.read_bytes(), b'previous backup')

    def test_sql_source_and_jar_fingerprint_agree(self):
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            sql = root / 'sql'
            sql.mkdir()
            (sql / 'V1__a.sql').write_bytes(b'CREATE TABLE example (id INT);\n')
            archive = root / 'app.jar'
            with zipfile.ZipFile(archive, 'w') as output:
                output.writestr('BOOT-INF/classes/db/migration/V1__a.sql', (sql / 'V1__a.sql').read_bytes())
                output.writestr('BOOT-INF/classes/db/local/R__seed.sql', b'not production')
            self.assertEqual(d.source_fingerprint(sql), d.jar_fingerprint(archive))
            before = d.source_fingerprint(sql)
            (sql / 'V2__b.sql').write_bytes(b'INSERT INTO example VALUES (1);')
            self.assertNotEqual(before, d.source_fingerprint(sql))

    def test_fingerprint_includes_name_and_ignores_order(self):
        self.assertEqual(d.fingerprint([('b', b'2'), ('a', b'1')]), d.fingerprint([('a', b'1'), ('b', b'2')]))
        self.assertNotEqual(d.fingerprint([('a', b'1')]), d.fingerprint([('b', b'1')]))

    def test_invalid_env_is_rejected(self):
        for text in ('A', 'A=1\nA=2', 'bad key=x'):
            with self.assertRaises(d.DeployError):
                d.parse_env(text)
        for name, value in [('SPRING_PROFILES_ACTIVE', 'local'), ('DB_PASSWORD', ''),
                            ('SPRING_FLYWAY_ENABLED', 'false'), ('SPRING_JPA_HIBERNATE_DDL_AUTO', 'update')]:
            env = d.parse_env(ENV)
            env[name] = value
            with self.assertRaises(d.DeployError):
                d.validate_env(env)

    def test_redirect_is_not_accepted_as_health(self):
        self.assertIsNone(d.NoRedirect().redirect_request(None, None, 302, '', {}, 'https://example.com'))

    def test_manifest_selects_only_linux_amd64(self):
        docker = d.Docker()
        entry = {'Descriptor': {'digest': DIGEST, 'platform': {'os': 'linux', 'architecture': 'amd64'}}}
        arm = {'Descriptor': {'digest': NEW_DIGEST, 'platform': {'os': 'linux', 'architecture': 'arm64'}}}
        docker.run = Mock(return_value=json.dumps([entry, arm]))
        self.assertEqual(docker.manifest('ghcr.io/test/backend', 'develop'), DIGEST)
        docker.run.return_value = json.dumps([arm])
        with self.assertRaises(d.DeployError):
            docker.manifest('ghcr.io/test/backend', 'develop')
        docker.run.return_value = json.dumps([entry, entry])
        with self.assertRaises(d.DeployError):
            docker.manifest('ghcr.io/test/backend', 'develop')

    def test_docker_errors_do_not_echo_secrets(self):
        with patch.object(d.subprocess, 'run', return_value=Mock(returncode=1, stdout='secret', stderr='secret')):
            with self.assertRaises(d.DeployError) as result:
                d.Docker().run('inspect', 'backend')
            self.assertNotIn('secret', str(result.exception))


if __name__ == '__main__':
    unittest.main()
