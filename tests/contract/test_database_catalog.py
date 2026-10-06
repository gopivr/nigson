"""Specification inventory and release-gate checks; real DB checks are separate."""
import importlib.util
import json
from pathlib import Path
import sys
import unittest

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/database'))
from catalog import catalog
from generate import generate, predicate


class DatabaseCatalogTest(unittest.TestCase):
    def test_reviewed_artifacts_match_normative_catalog(self):
        for path, content in generate().items():
            self.assertEqual((ROOT / path).read_text(), content, str(path))
        manifest = json.loads((ROOT / 'database/schema-manifest.json').read_text())
        self.assertEqual(len(manifest['tables']), 122)
        for name, table in manifest['tables'].items():
            self.assertTrue(table['creation_changeset'].startswith(table['owner'] + '-'))
            self.assertIn('CREATE TABLE ' + name + ' ', (ROOT / table['creation_file']).read_text())
            for field in table['fields']:
                if field['foreign']:
                    self.assertIn(table['owner'] + '.' + field['foreign'].split('.')[0], manifest['tables'])
                if field['external']:
                    self.assertIsNone(field['foreign'])

    def test_phase_wrappers_are_cumulative_without_contexts(self):
        for owner in ('commerce', 'experience', 'integration', 'ai'):
            base = ROOT / ('database/changelog/ai' if owner == 'ai' else f'services/{owner}/src/main/resources/db/changelog')
            for phase in range(1, 6):
                wrapper = json.loads((base / 'releases' / f'BP{phase}.yaml').read_text())['databaseChangeLog']
                if phase > 1:
                    self.assertEqual(wrapper[0]['include']['file'], f'BP{phase-1}.yaml')
                self.assertNotIn('context', json.dumps(wrapper))
                self.assertNotIn('labels', json.dumps(wrapper))

    def test_partial_state_index_literals(self):
        self.assertEqual(predicate('state IN (CREATED,INITIATING,PENDING,UNKNOWN)'), "state IN ('CREATED','INITIATING','PENDING','UNKNOWN')")
        sql = (ROOT / 'services/commerce/src/main/resources/db/changelog/021-bp1-indexes.sql').read_text()
        self.assertIn("WHERE state IN ('CREATED','INITIATING','PENDING','UNKNOWN')", sql)


if __name__ == '__main__':
    unittest.main()
