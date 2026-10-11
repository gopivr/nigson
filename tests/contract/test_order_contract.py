import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class OrderContracts(unittest.TestCase):
    def test_exact_seven_operations_and_security_headers(self):
        s=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());paths=s['paths']
        expected={'/api/v1/checkout/attempts':('post','API-037','201'),'/api/v1/orders':('get','API-038','200'),'/api/v1/orders/{id}':('get','API-039','200'),'/api/v1/orders/{id}/cancellation':('post','API-040','200'),'/api/v1/staff/orders':('get','API-043','200'),'/api/v1/staff/orders/{id}/processing':('post','API-044','200'),'/internal/v1/purchase-proofs':('post','API-121','201')}
        for route,(method,api,status) in expected.items():
            op=paths[route][method];self.assertEqual(op['operationId'],api);self.assertEqual(op['responses'][status]['headers']['Cache-Control']['schema'],{'const':'no-store'})
            self.assertEqual(op['security'],[{'experienceWorkload':['purchase-proof.read']}] if api=='API-121' else [{'bffSession':[]}])
            if api in {'API-037','API-040','API-044'}:self.assertTrue({'Idempotency-Key','X-CSRF-TOKEN'}.issubset({p['name'] for p in op['parameters'] if p['required']}))
            if api in {'API-040','API-044'}:self.assertIn('If-Match',{p['name'] for p in op['parameters']})
        self.assertIn('202',paths['/api/v1/checkout/attempts']['post']['responses'])
    def test_frozen_values_are_closed_and_private_proof_is_boolean_only(self):
        s=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())['components']['schemas']
        for name in ['CheckoutInput','OrderDTO','OrderLineDTO','OrderTotals','ProofDTO','PurchaseProofInput','OrderAddressSnapshot']:self.assertFalse(s[name]['additionalProperties'])
        self.assertEqual(set(s['ProofDTO']['properties']),{'eligible','reasonCode','revision'})
        self.assertEqual(s['OrderDTO']['properties']['lines']['maxItems'],100)
        self.assertEqual(s['OrderPage']['properties']['items']['maxItems'],100)
        self.assertNotIn('id',s['OrderAddressSnapshot']['properties']);self.assertNotIn('version',s['OrderAddressSnapshot']['properties']);self.assertNotIn('merchantAccount',s['CheckoutPayment']['properties'])
        self.assertEqual(s['CheckoutInput']['properties']['paymentMethod'],{'const':'HOSTED'})
