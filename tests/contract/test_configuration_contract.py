import json
from pathlib import Path
import unittest

ROOT = Path(__file__).resolve().parents[2]

class ConfigurationContract(unittest.TestCase):
    def test_exact_closed_configuration_contract(self):
        spec = json.loads((ROOT / 'contracts/openapi/commerce.yaml').read_text())
        op = spec['paths']['/api/v1/commerce/staff/configuration/{key}']['put']
        self.assertEqual(op['operationId'], 'API-135')
        self.assertEqual(op['security'], [{'bffSession': []}])
        self.assertEqual({p['name'] for p in op['parameters']}, {'key', 'If-Match', 'Idempotency-Key', 'X-CSRF-TOKEN'})
        for name, fields in [('ConfigurationDTO', {'key', 'revision', 'active', 'version'}), ('ConfigurationChange', {'value', 'active', 'reason'}), ('CatalogueMetadata', {'schemaVersion', 'units', 'divisions'})]:
            schema = spec['components']['schemas'][name]
            self.assertFalse(schema['additionalProperties'])
            self.assertEqual(set(schema['required']), fields)
        self.assertIn('412', op['responses'])
        self.assertIn('422', op['responses'])
        self.assertNotIn('202', op['responses'])
