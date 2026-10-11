import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class CartContracts(unittest.TestCase):
    def test_exact_private_cart_routes(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        paths=spec['paths'];route='/api/v1/carts/{id}/lines/{skuId}'
        self.assertEqual({paths['/api/v1/carts']['post']['operationId'],paths['/api/v1/carts/{id}']['get']['operationId'],*(op['operationId'] for op in paths[route].values())},{'API-032','API-033','API-034','API-035'})
        for op in [paths['/api/v1/carts']['post'],paths['/api/v1/carts/{id}']['get'],*paths[route].values()]:
            self.assertEqual(op['security'],[{'bffSession':[]}])
            success=next(code for code in ('200','201','204') if code in op['responses'])
            self.assertEqual(op['responses'][success]['headers']['Cache-Control']['schema'],{'const':'no-store'})
        self.assertNotIn('content',paths[route]['delete']['responses']['204'])
        for method in ['put','delete']:
            self.assertTrue({'id','skuId','If-Match','Idempotency-Key','X-CSRF-TOKEN'}.issubset({p['name'] for p in paths[route][method]['parameters'] if p.get('required')}))
    def test_closed_inputs_and_bounded_aggregate(self):
        s=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())['components']['schemas']
        self.assertEqual(set(s['NewCart']['properties']),{'marketId','organizationId'})
        self.assertEqual(set(s['CartQuantity']['properties']),{'quantity'})
        self.assertFalse(s['NewCart']['additionalProperties']);self.assertFalse(s['CartQuantity']['additionalProperties'])
        self.assertEqual(s['CartDTO']['properties']['lines']['maxItems'],100)
        self.assertEqual(s['CartDTO']['properties']['state']['enum'],['ACTIVE','CHECKOUT_PENDING','CONVERTED','EXPIRED'])
