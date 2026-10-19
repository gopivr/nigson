import json
from pathlib import Path
import unittest

ROOT=Path(__file__).resolve().parents[2]

class CartRecoveryContract(unittest.TestCase):
    def test_api159_has_exact_private_scope_closed_dto_and_no_write_headers(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        operation=spec['paths']['/internal/v1/cart-recovery/{id}/eligibility']['post']
        self.assertEqual(operation['operationId'],'API-159')
        self.assertEqual(operation['security'],[{'cartRecoveryWorkload':['recovery.read']}])
        self.assertTrue(operation['requestBody']['required'])
        self.assertEqual(operation['requestBody']['content']['application/json']['schema']['properties'],{})
        self.assertFalse(spec['components']['schemas']['CartRecoveryEligibility']['additionalProperties'])
        self.assertEqual(set(spec['components']['schemas']['CartRecoveryEligibility']['properties']),{'eligible','partyId','cartLink'})
        self.assertEqual(operation['responses']['200']['headers']['Cache-Control']['schema']['const'],'no-store')
        self.assertNotIn('If-Match',{p['name'] for p in operation['parameters']})
        self.assertNotIn('Idempotency-Key',{p['name'] for p in operation['parameters']})
        routes=json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())
        self.assertEqual(next(route for route in routes if route['api']=='API-159'),{
            'api':'API-159','method':'POST','path':'/internal/v1/cart-recovery/{id}/eligibility',
            'owner':'commerce','client':'nigson-integration','scope':'recovery.read','phase':4})

if __name__=='__main__':
    unittest.main()
