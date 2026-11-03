"""Safety and referential integrity checks for local demo fixtures."""
import importlib.util
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location('seed_demo', ROOT / 'scripts/local/seed_demo.py')
seed = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(seed)


class DemoSeedTest(unittest.TestCase):
    def test_required_foreign_keys_reference_fixture_keys(self):
        rows = dict(seed.rows_for(seed.TABLES))
        for table, row in rows.items():
            for field in seed.TABLES[table]['fields']:
                if field['foreign'] and not field['nullable']:
                    target, _, column = field['foreign'].partition('.')
                    self.assertEqual(row[field['name']], rows[table.split('.')[0] + '.' + target][column or 'id'])

    def test_worker_records_are_terminal(self):
        for table, column, terminal in (
            ('integration.notification_job', 'state', 'SUPPRESSED'),
            ('integration.notification_dispatch', 'stage', 'SUPPRESSED'),
            ('integration.integration_job', 'state', 'SUCCEEDED'),
            ('integration.carrier_booking', 'state', 'CANCELLED'),
            ('commerce.payment_attempt', 'state', 'FAILED'),
            ('commerce.carrier_cancellation', 'state', 'CANCELLED'),
            ('experience.file_transfer', 'state', 'REJECTED'),
            ('ai.ingestion_job', 'status', 'SUCCEEDED'),
        ):
            field = next(f for f in seed.TABLES[table]['fields'] if f['name'] == column)
            self.assertEqual(seed.value(table, field), terminal)
        for owner in ('commerce', 'experience', 'integration', 'ai'):
            field = next(f for f in seed.TABLES[owner + '.outbox_event']['fields'] if f['name'] == 'state')
            self.assertEqual(seed.value(owner + '.outbox_event', field), 'PUBLISHED')

    def test_repeat_sql_is_deterministic_and_atomic(self):
        sql = seed.render(seed.TABLES)
        self.assertEqual(sql, seed.render(seed.TABLES))
        self.assertTrue(sql.startswith('BEGIN;'))
        self.assertIn('COMMIT;', sql)
        self.assertNotIn('DELETE FROM', sql)
        self.assertNotIn('TRUNCATE', sql)
        self.assertNotIn('ON CONFLICT ()', sql)
        self.assertIn('ON CONFLICT (session_primary_id,attribute_name) DO NOTHING', sql)

    def test_commercial_configuration_stays_disabled(self):
        for table, column in (('commerce.market', 'enabled'), ('commerce.price_book', 'active'),
                              ('commerce.delivery_rule', 'active'), ('commerce.promotion_rule', 'enabled')):
            field = next(f for f in seed.TABLES[table]['fields'] if f['name'] == column)
            self.assertFalse(seed.value(table, field))

    def test_missing_dependencies_fail_before_sql(self):
        with self.assertRaises(ValueError):
            seed.render(['commerce.cart_line'])


if __name__ == '__main__':
    unittest.main()
