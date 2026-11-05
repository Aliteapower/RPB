"""Run with python -m unittest discover -s ops/tests -p test_workforce_payroll_development_reset.py."""
import importlib.util
from pathlib import Path
import re
import unittest
import uuid

import psycopg
from psycopg import sql

ROOT = Path(__file__).resolve().parents[2]
spec = importlib.util.spec_from_file_location('payroll_reset', ROOT / 'ops/workforce_payroll_development_reset.py')
reset = importlib.util.module_from_spec(spec)
spec.loader.exec_module(reset)


class ResetTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        # Never fall back to a service/default port or an existing application schema.
        pointer = dict(line.split('=', 1) for line in (ROOT / 'target/local-postgres-current.txt').read_text().splitlines() if '=' in line)
        cls.conn = psycopg.connect(host='127.0.0.1', port=int(pointer['port']), dbname='postgres', user='postgres', password='', autocommit=True)
        cls.schemas = []

    @classmethod
    def tearDownClass(cls):
        print('Retained synthetic schemas: ' + ', '.join(cls.schemas))
        cls.conn.close()

    def setUp(self):
        self.schema = 'payroll_reset_test_' + uuid.uuid4().hex[:12]
        self.schemas.append(self.schema)
        c = self.conn
        c.execute(sql.SQL('CREATE SCHEMA {}').format(sql.Identifier(self.schema)))
        for table in reset.TABLES:
            c.execute(sql.SQL('CREATE TABLE {}.{} (id integer PRIMARY KEY)').format(sql.Identifier(self.schema), sql.Identifier(table)))
            c.execute(sql.SQL('INSERT INTO {}.{} VALUES (1)').format(sql.Identifier(self.schema), sql.Identifier(table)))
        c.execute(sql.SQL('CREATE TABLE {}.workforce_employees(id integer PRIMARY KEY)').format(sql.Identifier(self.schema)))
        c.execute(sql.SQL('INSERT INTO {}.workforce_employees VALUES (99)').format(sql.Identifier(self.schema)))
        c.execute(sql.SQL('ALTER TABLE {}.workforce_pay_inputs ADD COLUMN batch_id integer REFERENCES {}.workforce_pay_batches(id)').format(sql.Identifier(self.schema), sql.Identifier(self.schema)))
        c.execute(sql.SQL('UPDATE {}.workforce_pay_inputs SET batch_id=1').format(sql.Identifier(self.schema)))
        c.execute(sql.SQL("CREATE FUNCTION {}.immutable() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN RAISE EXCEPTION 'immutable'; END $$").format(sql.Identifier(self.schema)))
        c.execute(sql.SQL('CREATE TRIGGER immutable BEFORE DELETE OR UPDATE ON {}.workforce_pay_inputs FOR EACH ROW EXECUTE FUNCTION {}.immutable()').format(sql.Identifier(self.schema), sql.Identifier(self.schema)))

    def rows(self, name):
        return self.conn.execute(sql.SQL('SELECT count(*) FROM {}.{}').format(sql.Identifier(self.schema), sql.Identifier(name))).fetchone()[0]

    def test_preview_then_apply_preserves_employee_and_trigger(self):
        result = reset.run(self.conn, self.schema, 'postgres')
        self.assertEqual(set(reset.TABLES), set(result))
        self.assertTrue(all(n == 1 for n in result.values()))
        self.assertEqual(1, self.rows('workforce_pay_inputs'))
        reset.run(self.conn, self.schema, 'postgres', apply=True)
        self.assertTrue(all(self.rows(t) == 0 for t in reset.TABLES))
        self.assertEqual(1, self.rows('workforce_employees'))
        self.conn.execute(sql.SQL('INSERT INTO {}.workforce_pay_inputs(id) VALUES (2)').format(sql.Identifier(self.schema)))
        with self.assertRaises(psycopg.errors.RaiseException):
            self.conn.execute(sql.SQL('DELETE FROM {}.workforce_pay_inputs').format(sql.Identifier(self.schema)))

    def test_external_fk_rejected_even_when_empty(self):
        self.conn.execute(sql.SQL('CREATE TABLE {}.unrelated (id integer REFERENCES {}.workforce_pay_batches(id))').format(sql.Identifier(self.schema), sql.Identifier(self.schema)))
        with self.assertRaisesRegex(ValueError, 'external foreign key'):
            reset.run(self.conn, self.schema, 'postgres', apply=True)
        self.assertEqual(1, self.rows('workforce_pay_batches'))

    def test_truncate_trigger_rejected(self):
        self.conn.execute(sql.SQL('CREATE TRIGGER truncate_side_effect BEFORE TRUNCATE ON {}.workforce_pay_inputs EXECUTE FUNCTION {}.immutable()').format(sql.Identifier(self.schema), sql.Identifier(self.schema)))
        with self.assertRaisesRegex(ValueError, 'TRUNCATE trigger'):
            reset.run(self.conn, self.schema, 'postgres', apply=True)
        self.assertEqual(1, self.rows('workforce_pay_inputs'))

    def test_identity_and_missing_tables_rejected(self):
        with self.assertRaisesRegex(ValueError, 'database mismatch'):
            reset.run(self.conn, self.schema, 'not_postgres', apply=True)
        with self.assertRaisesRegex(ValueError, 'schema'):
            reset.run(self.conn, 'missing_schema', 'postgres', apply=True)
        self.conn.execute(sql.SQL('ALTER TABLE {}.workforce_pay_payments RENAME TO renamed').format(sql.Identifier(self.schema)))
        with self.assertRaisesRegex(ValueError, 'missing'):
            reset.run(self.conn, self.schema, 'postgres', apply=True)
        self.assertEqual(1, self.rows('workforce_pay_batches'))

    def test_inheritance_rejected(self):
        self.conn.execute(sql.SQL('CREATE TABLE {}.unexpected_child () INHERITS ({}.workforce_pay_batches)').format(sql.Identifier(self.schema), sql.Identifier(self.schema)))
        with self.assertRaisesRegex(ValueError, 'inheritance'):
            reset.run(self.conn, self.schema, 'postgres', apply=True)
        self.assertEqual(1, self.rows('workforce_pay_batches'))

    def test_exact_migration_manifest(self):
        expected = set()
        for version in (91, 103, 105, 107, 108, 109, 110, 111, 112):
            path = next((ROOT / 'src/main/resources/db/migration').glob(f'V{version:03}__*.sql'))
            expected.update(re.findall(r'create table\s+(\w+)', path.read_text(), re.I))
        self.assertEqual(expected, set(reset.TABLES))
        self.assertNotIn('workforce_statutory_policies', reset.TABLES)

    def test_external_view_rejected(self):
        self.conn.execute(sql.SQL('CREATE VIEW {}.external_report AS SELECT * FROM {}.workforce_pay_batches').format(sql.Identifier(self.schema), sql.Identifier(self.schema)))
        with self.assertRaisesRegex(ValueError, 'external view'):
            reset.run(self.conn, self.schema, 'postgres', apply=True)
        self.assertEqual(1, self.rows('workforce_pay_batches'))

    def test_real_migrations_fk_graph(self):
        schema = 'payroll_reset_migrations_' + uuid.uuid4().hex[:12]
        self.schemas.append(schema)
        with self.conn.transaction():
            self.conn.execute(sql.SQL('CREATE SCHEMA {}').format(sql.Identifier(schema)))
            self.conn.execute(sql.SQL('SET LOCAL search_path TO {}, public').format(sql.Identifier(schema)))
            self.conn.execute('CREATE TABLE tenants(id uuid PRIMARY KEY)')
            # External owners are minimal synthetic prerequisites, never existing business tables.
            for table in ('operating_entities', 'auth_accounts', 'stores', 'workforce_employees', 'workforce_positions'):
                self.conn.execute(sql.SQL('CREATE TABLE {} (id uuid PRIMARY KEY, tenant_id uuid NOT NULL, UNIQUE(id,tenant_id))').format(sql.Identifier(table)))
            for version in (91, 103, 105, 107, 108, 109, 110, 111, 112):
                path = next((ROOT / 'src/main/resources/db/migration').glob(f'V{version:03}__*.sql'))
                self.conn.execute(path.read_text(), prepare=False)
            self.conn.execute("INSERT INTO workforce_employees VALUES ('00000000-0000-0000-0000-000000000001','00000000-0000-0000-0000-000000000002')")
            self.conn.execute("INSERT INTO operating_entities SELECT * FROM workforce_employees")
            self.conn.execute("INSERT INTO workforce_statutory_year_subjects(tenant_id,company_id,employee_id,year) SELECT tenant_id,id,id,2026 FROM workforce_employees")
            self.conn.execute("INSERT INTO workforce_statutory_year_revisions(tenant_id,company_id,employee_id,year,version,snapshot,created_by) SELECT tenant_id,id,id,2026,1,'{}',id FROM workforce_employees")
        counts = reset.run(self.conn, schema, 'postgres')
        self.assertEqual(1, counts['workforce_statutory_year_revisions'])
        reset.run(self.conn, schema, 'postgres', apply=True)
        self.assertTrue(all(n == 0 for n in reset.run(self.conn, schema, 'postgres').values()))
        self.assertEqual(1, self.conn.execute(sql.SQL('SELECT count(*) FROM {}.workforce_employees').format(sql.Identifier(schema))).fetchone()[0])


if __name__ == '__main__':
    unittest.main()
