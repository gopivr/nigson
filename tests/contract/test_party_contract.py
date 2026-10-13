import json
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[2]
class PartyContracts(unittest.TestCase):
    def test_exact_owned_contracts(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        operations=[op for path in spec['paths'].values() for op in path.values()]
        self.assertEqual({op['operationId'] for op in operations},{f'API-{n:03}' for n in range(1,26)} | {'API-111', 'API-112', 'API-113', 'API-132', 'API-135', 'API-149', 'API-163', 'API-164', 'API-122', 'API-133', 'API-165', 'API-045', 'API-046', 'API-170', 'API-029', 'API-031', 'API-162', 'API-169', 'API-026', 'API-027', 'API-028', 'API-036', 'API-150', 'API-151', 'API-032', 'API-033', 'API-034', 'API-035', 'API-047', 'API-048', 'API-049', 'API-050', 'API-171', 'API-174', 'API-037', 'API-038', 'API-039', 'API-040', 'API-043', 'API-044', 'API-121', 'API-041', 'API-042', 'API-052', 'API-053', 'API-054', 'API-118', 'API-181', 'API-119'})
        self.assertFalse(spec['components']['schemas']['ProfilePatch']['additionalProperties'])
        self.assertEqual(set(spec['components']['schemas']['ProfilePatch']['properties']),{'displayName','phone','locale'})
        self.assertNotIn('partyId',spec['components']['schemas']['AddressInput']['properties'])
        self.assertNotIn('content',spec['paths']['/api/v1/me/addresses/{id}']['delete']['responses']['204'])
        self.assertIn('201',spec['paths']['/api/v1/me/addresses']['post']['responses'])
    def test_closed_address_policy_schema(self):
        schema=json.loads((ROOT/'contracts/rules/address-rules.schema.json').read_text())
        self.assertFalse(schema['additionalProperties'])
        self.assertEqual(set(schema['required']),{'schemaVersion','requiredFields','postalPattern','regionCodes'})
        self.assertEqual(schema['properties']['schemaVersion'],{'const':1})
        self.assertTrue(schema['properties']['requiredFields']['uniqueItems'])
        self.assertEqual(schema['properties']['postalPattern']['anyOf'][0]['maxLength'],120)
