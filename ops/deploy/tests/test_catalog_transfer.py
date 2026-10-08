import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import Mock, patch


MODULE = Path(__file__).resolve().parents[1] / 'catalog_transfer.py'
spec = importlib.util.spec_from_file_location('sebu_catalog_transfer', MODULE)
c = importlib.util.module_from_spec(spec)
spec.loader.exec_module(c)


def raw(values):
    return json.dumps(values, ensure_ascii=False, separators=(', ', ': '))


def bundle():
    schema = [['table', name, 'InnoDB'] for name in sorted(c.KNOWN_TABLES)]
    tables = {}
    for name in c.TABLES:
        columns = ['id', 'parent_id'] if name == 'research_field_category' else ['id']
        for column in columns:
            schema.append(['column', name, column, 'bigint', 'YES', None, '', '', None, None])
        schema.append(['key', name, 'PRIMARY', 'id', 1, None, None])
        tables[name] = c.seal_table(columns, [raw(['1', None] if len(columns) == 2 else ['1'])])
    return {
        'format': 1, 'source': {'server_uuid': 'source-uuid', 'database': 'sebu'},
        'schema': schema, 'migrations': [['1', 'initial', 'SQL', 'V1__initial.sql', '123', '1']],
        'excluded_counts': {name: 0 for name in c.EXCLUDED}, 'tables': tables,
    }


def metadata_output(data, include_rows=False):
    lines = [['server', data['source']['server_uuid'], data['source']['database']]]
    lines += data['schema']
    lines += [['migration', *row] for row in data['migrations']]
    if include_rows:
        lines += [['excluded', table, count] for table, count in data['excluded_counts'].items()]
        lines += [['row', name, row] for name, table in data['tables'].items() for row in table['rows']]
    return '\n'.join(json.dumps(line, ensure_ascii=False) for line in lines)


class CatalogueBundleTests(unittest.TestCase):
    def test_valid_bundle_round_trips_privately_without_extra_tables(self):
        data = bundle()
        data['excluded_counts']['laboratory_review'] = 8
        with tempfile.TemporaryDirectory() as directory:
            destination = Path(directory) / 'catalog.json.gz'
            c.write_bundle(destination, data)
            self.assertEqual(c.read_bundle(destination), data)
        self.assertEqual(len(c.TABLES), 11)
        self.assertNotIn('laboratory_review', data['tables'])
        self.assertNotIn('app_user', data['tables'])
        self.assertNotIn('flyway_schema_history', data['tables'])

    def test_snapshot_uses_consistent_read_only_transaction_and_exports_catalogue_only(self):
        data = bundle()
        client = Mock()
        client.run.side_effect = [metadata_output(data), metadata_output(data, include_rows=True)]
        actual = c.snapshot(client)
        self.assertEqual(actual['tables'], data['tables'])
        sql = client.run.call_args.args[0]
        self.assertIn('SET TRANSACTION READ ONLY', sql)
        self.assertIn('START TRANSACTION WITH CONSISTENT SNAPSHOT', sql)
        self.assertNotIn('INSERT INTO', sql)
        self.assertNotIn('DELETE FROM', sql)
        self.assertNotIn('UPDATE ', sql)
        # Excluded tables supply COUNT(*) only, never their user content.
        for table in c.EXCLUDED:
            self.assertNotIn('FROM `' + table + '` ORDER BY', sql)

    def test_unknown_table_and_excluded_payload_are_rejected(self):
        for unexpected in ('unreviewed_future_table', 'laboratory_review', 'app_user'):
            with self.subTest(unexpected=unexpected):
                data = bundle()
                data['tables'][unexpected] = c.seal_table(['id'], [raw(['8'])])
                with self.assertRaises(c.TransferError):
                    c.validate_bundle(data)
        data = bundle()
        data['schema'].append(['table', 'new_table', 'InnoDB'])
        with self.assertRaises(c.TransferError):
            c.validate_bundle(data)

    def test_missing_table_or_exclusion_audit_is_rejected(self):
        for field, name in (('tables', 'professor'), ('excluded_counts', 'laboratory_review')):
            data = bundle()
            del data[field][name]
            with self.assertRaises(c.TransferError):
                c.validate_bundle(data)

    def test_changed_row_count_or_checksum_is_rejected(self):
        for key, value in (('rows', [raw(['999'])]), ('count', 2), ('sha256', '0' * 64)):
            with self.subTest(key=key):
                data = bundle()
                data['tables']['professor'][key] = value
                with self.assertRaises(c.TransferError):
                    c.validate_bundle(data)

    def test_duplicate_primary_key_is_rejected_even_with_resealed_checksum(self):
        data = bundle()
        data['tables']['professor'] = c.seal_table(['id'], [raw(['1']), raw(['1'])])
        with self.assertRaises(c.TransferError):
            c.validate_bundle(data)

    def test_nonstring_cells_and_wrong_row_width_are_rejected(self):
        for values in ([1], [True], [{'sql': 'DROP TABLE'}], ['1', '2']):
            data = bundle()
            data['tables']['professor'] = c.seal_table(['id'], [raw(values)])
            with self.assertRaises(c.TransferError):
                c.validate_bundle(data)

    def test_cycles_missing_parents_and_self_parent_are_rejected(self):
        for rows in ((['1', '2'], ['2', '1']), (['1', '999'],), (['1', '1'],)):
            with self.subTest(rows=rows):
                data = bundle()
                data['tables']['research_field_category'] = c.seal_table(['id', 'parent_id'], [raw(row) for row in rows])
                with self.assertRaises(c.TransferError):
                    c.validate_bundle(data)

    def test_parent_is_inserted_first_even_if_child_has_smaller_id(self):
        table = c.seal_table(['id', 'parent_id'], [raw(['1', '2']), raw(['2', None])])
        self.assertEqual(c.category_order(table), [raw(['2', None]), raw(['1', '2'])])

    def test_generated_columns_are_omitted_but_timestamp_defaults_are_kept(self):
        schema = [
            ['column', 'professor', 'created_at', 'timestamp', 'NO', 'CURRENT_TIMESTAMP', 'DEFAULT_GENERATED', '', None, None],
            ['column', 'professor', 'computed_name', 'varchar(100)', 'YES', None, 'VIRTUAL GENERATED', 'lower(name)', 'utf8mb4', 'utf8mb4_0900_ai_ci'],
        ]
        self.assertEqual(c.schema_columns(schema, 'professor'), ['created_at'])

    def test_nontransactional_tables_failed_migrations_and_excluded_foreign_key_fail_closed(self):
        data = bundle()
        data['schema'][0][2] = 'MyISAM'
        with self.assertRaises(c.TransferError):
            c.validate_bundle(data)
        data = bundle()
        data['migrations'][0][-1] = '0'
        with self.assertRaises(c.TransferError):
            c.validate_bundle(data)
        data = bundle()
        data['schema'].append(['key', 'professor', 'unexpected_author', 'id', 1, 'app_user', 'id'])
        with self.assertRaises(c.TransferError):
            c.validate_bundle(data)

    def test_schema_drift_between_discovery_and_consistent_read_is_rejected(self):
        first, second = bundle(), bundle()
        second['migrations'][0][4] = 'changed-checksum'
        client = Mock()
        client.run.side_effect = [metadata_output(first), metadata_output(second, include_rows=True)]
        with self.assertRaises(c.TransferError):
            c.snapshot(client)

    def test_sql_values_cannot_become_statements_or_shell_arguments(self):
        text = "한글 🧪\t\n'; DROP DATABASE sebu; -- \\ $(secret) `whoami`"
        literal = c.sql_literal(text)
        self.assertNotIn('DROP', literal)
        self.assertNotIn('$(secret)', literal)
        encoded = literal.split("'")[1]
        self.assertEqual(bytes.fromhex(encoded).decode('utf-8'), text)
        self.assertEqual(c.sql_literal(None), 'NULL')
        for value in (1, False, ['x']):
            with self.assertRaises(c.TransferError):
                c.sql_literal(value)
        for value in ('x; DROP DATABASE sebu', 'x`', 'x.y', '-e', 'x\n'):
            with self.assertRaises(c.TransferError):
                c.identifier(value)
        with self.assertRaises(c.TransferError):
            c.DockerMySQL('--privileged', 'sebu')

    def test_mysql_errors_do_not_print_professor_data_or_passwords(self):
        result = subprocess.CompletedProcess([], 1, '', "ERROR 1062 Duplicate '홍길동-private-email@example.invalid' MYSQL_PWD=secret")
        with patch.object(c.subprocess, 'run', return_value=result) as run:
            with self.assertRaises(c.TransferError) as failure:
                c.DockerMySQL('mysql', 'sebu').run('SELECT 1;')
            self.assertIn('1062', str(failure.exception))
            self.assertNotIn('홍길동', str(failure.exception))
            self.assertNotIn('secret', str(failure.exception))
            self.assertFalse(run.call_args.kwargs.get('shell', False))


class CatalogueRestoreTests(unittest.TestCase):
    def setUp(self):
        self.source = bundle()
        self.before = bundle()
        self.before['source'] = {'server_uuid': 'destination-uuid', 'database': 'sebu_catalog_verify_test'}
        self.client = Mock(database='sebu_catalog_verify_test')

    def test_confirm_target_is_required_before_any_database_call(self):
        with self.assertRaises(c.TransferError):
            c.restore(self.client, self.source, 'sebu')
        self.client.run.assert_not_called()

    def test_production_requires_existing_stopped_application(self):
        self.client.database = 'sebu_prod'
        with self.assertRaises(c.TransferError):
            c.restore(self.client, self.source, 'sebu_prod')
        self.client.run.assert_not_called()
        self.client.app_stopped.side_effect = c.TransferError('running')
        with self.assertRaises(c.TransferError):
            c.restore(self.client, self.source, 'sebu_prod', 'sebu-prod-backend')
        self.client.app_stopped.assert_called_once_with('sebu-prod-backend')
        self.client.run.assert_not_called()

    def test_source_database_cannot_be_overwritten(self):
        self.before['source'] = self.source['source']
        with patch.object(c, 'snapshot', return_value=self.before):
            with self.assertRaises(c.TransferError):
                c.restore(self.client, self.source, self.client.database)
        self.client.run.assert_not_called()

    def test_user_activity_or_candidates_on_target_prevent_any_delete(self):
        for table in c.EXCLUDED:
            with self.subTest(table=table):
                before = copy.deepcopy(self.before)
                before['excluded_counts'][table] = 1
                with patch.object(c, 'snapshot', return_value=before):
                    with self.assertRaises(c.TransferError):
                        c.restore(self.client, self.source, self.client.database)
        self.client.run.assert_not_called()

    def test_schema_or_migration_mismatch_prevents_any_write(self):
        for field in ('schema', 'migrations'):
            with self.subTest(field=field):
                before = copy.deepcopy(self.before)
                before[field] = []
                with patch.object(c, 'snapshot', return_value=before):
                    with self.assertRaises(c.TransferError):
                        c.restore(self.client, self.source, self.client.database)
        self.client.run.assert_not_called()

    def test_restore_keeps_foreign_keys_and_has_one_transaction_and_verification_before_commit(self):
        self.client.run.return_value = '["committed"]\n'
        with patch.object(c, 'snapshot', return_value=self.before), patch.object(c, 'verify') as verify:
            c.restore(self.client, self.source, self.client.database)
        sql = self.client.run.call_args.args[0]
        self.assertEqual(sql.count('START TRANSACTION'), 1)
        self.assertEqual(sql.count('COMMIT;'), 1)
        self.assertNotIn('FOREIGN_KEY_CHECKS', sql)
        self.assertNotIn('TRUNCATE', sql)
        self.assertNotIn('DROP TABLE', sql)
        self.assertNotIn('DELETE FROM `flyway_schema_history`', sql)
        self.assertNotIn('INSERT INTO `laboratory_review`', sql)
        self.assertLess(sql.index('DELETE FROM `laboratory_research_field`'), sql.index('DELETE FROM `laboratory`'))
        self.assertLess(sql.index('INSERT INTO `professor`'), sql.index('INSERT INTO `laboratory`'))
        self.assertLess(sql.rindex('SHA2('), sql.index('COMMIT;'))
        verify.assert_called_once_with(self.client, self.source)

    def test_commit_failure_is_not_reported_as_success(self):
        self.client.run.side_effect = c.TransferError('MySQL command failed')
        with patch.object(c, 'snapshot', return_value=self.before), patch.object(c, 'verify') as verify:
            with self.assertRaises(c.TransferError):
                c.restore(self.client, self.source, self.client.database)
        verify.assert_not_called()

    def test_missing_commit_acknowledgement_requires_verification(self):
        self.client.run.return_value = ''
        with patch.object(c, 'snapshot', return_value=self.before):
            with self.assertRaisesRegex(c.TransferError, 'acknowledgement'):
                c.restore(self.client, self.source, self.client.database)

    def test_verify_detects_equal_count_but_different_catalogue_data(self):
        self.before['tables']['professor'] = c.seal_table(['id'], [raw(['999'])])
        with patch.object(c, 'snapshot', return_value=self.before):
            with self.assertRaisesRegex(c.TransferError, 'professor'):
                c.verify(self.client, self.source)


if __name__ == '__main__':
    unittest.main()
