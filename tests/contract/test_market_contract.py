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
    def test_market_commands_require_scope_version_and_public_list_has_no_staff_fields(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        update=spec['paths']['/api/v1/staff/markets/{id}']['put'];self.assertEqual(update['operationId'],'API-030');self.assertIn('422',update['responses']);self.assertIn('approval',update['description'].lower())
        self.assertEqual(set(spec['components']['schemas']['MarketUpdate']['properties']),{'name','currency','enabled','destinations','taxPolicy','returnsPolicyRevision'})
        for path in ['/api/v1/staff/markets/{id}','/api/v1/staff/markets/{id}/retail-price-book','/api/v1/staff/products/{id}/markets/{marketId}']:
            required={p['name'] for p in spec['paths'][path]['put']['parameters'] if p['required']};self.assertTrue({'If-Match','X-CSRF-TOKEN','Idempotency-Key'}<=required)
        public=spec['components']['schemas']['PublicMarket'];self.assertEqual(set(public['properties']),{'id','code','currency','destinations','policyRevision'});self.assertFalse(public['additionalProperties'])
