"""Phone proof and BFF/private trust remain closed and owner scoped."""
import json
import subprocess
import sys
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
class PhoneContracts(unittest.TestCase):
    def test_generated_experience_routes_and_exact_scopes(self):
        subprocess.run([sys.executable,'scripts/contracts/phone.py','--check'],cwd=ROOT,check=True)
        spec=json.loads((ROOT/'contracts/openapi/experience.yaml').read_text())
        self.assertEqual({op['operationId'] for path in spec['paths'].values() for op in path.values()},{'API-127','API-175','API-187','API-201','API-202','API-203','API-204'})
        scopes={row['api']:row for row in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())}
        for number in range(201,205):
            self.assertEqual(scopes[f'API-{number}']['owner'],'experience')
            self.assertEqual(scopes[f'API-{number}']['client'],'nigson-commerce')
            self.assertTrue(scopes[f'API-{number}']['scope'].endswith('_WITH_SIGNED_CUSTOMER'))
    def test_public_masked_dto_and_explicit_channel_purpose(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());schemas=spec['components']['schemas']
        self.assertEqual(set(schemas['PhoneDestination']['properties']),{'id','channel','maskedAddress','status','version'})
        self.assertFalse(schemas['PhoneDestination']['additionalProperties'])
        self.assertEqual(schemas['PhoneDestinationInput']['properties']['consentedPurposes']['items']['enum'],['ORDER_UPDATES','MARKETING','CART_RECOVERY'])
        verify=spec['paths']['/api/v1/me/communication-destinations/{id}/verification']['post']
        self.assertEqual({p['name'] for p in verify['parameters'] if p['required']},{'id','Idempotency-Key','X-CSRF-TOKEN','If-Match'})
        self.assertEqual(set(schemas['PhoneProofPrivate']['properties']),{'code','version'})
