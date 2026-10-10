import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class MarketContracts(unittest.TestCase):
    def test_draft_creation_cannot_receive_business_approvals(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());s=spec['components']['schemas']
        self.assertEqual(set(s['NewMarket']['properties']),{'code','name','currency'});self.assertFalse(s['NewMarket']['additionalProperties'])
        op=spec['paths']['/api/v1/staff/markets']['post'];self.assertEqual(op['operationId'],'API-169');self.assertIn('201',op['responses']);self.assertNotIn('If-Match',{p['name'] for p in op['parameters']})
        self.assertEqual(s['TaxPolicy']['properties']['rounding']['const'],'HALF_UP');self.assertFalse(s['TaxPolicy']['additionalProperties']);self.assertIn('approvalReference',s['TaxPolicy']['required'])
    def test_both_updates_require_version_and_public_list_has_no_staff_fields(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        for path in ['/api/v1/staff/markets/{id}/retail-price-book','/api/v1/staff/products/{id}/markets/{marketId}']:
            required={p['name'] for p in spec['paths'][path]['put']['parameters'] if p['required']};self.assertTrue({'If-Match','X-CSRF-TOKEN','Idempotency-Key'}<=required)
        public=spec['components']['schemas']['PublicMarket'];self.assertEqual(set(public['properties']),{'id','code','currency','destinations','policyRevision'});self.assertFalse(public['additionalProperties'])
