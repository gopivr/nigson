import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class OrderContracts(unittest.TestCase):
    def test_exact_order_operations_and_security_headers(self):
        s=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());paths=s['paths']
        expected={'/api/v1/checkout/attempts':('post','API-037','201'),'/api/v1/orders':('get','API-038','200'),'/api/v1/orders/{id}':('get','API-039','200'),'/api/v1/orders/{id}/cancellation':('post','API-040','200'),'/api/v1/orders/{id}/returns':('post','API-238','201'),'/api/v1/returns/{id}':('get','API-239','200'),'/api/v1/staff/orders':('get','API-043','200'),'/api/v1/staff/orders/{id}/processing':('post','API-044','200'),'/api/v1/staff/returns/{id}/review':('post','API-240','200'),'/api/v1/staff/returns/{id}/receipt':('post','API-241','200'),'/api/v1/staff/returns/{id}/refund':('post','API-242','201'),'/internal/v1/purchase-proofs':('post','API-121','201'),'/internal/v1/ai/order-status':('post','API-124','200')}
        for route,(method,api,status) in expected.items():
            op=paths[route][method];self.assertEqual(op['operationId'],api);self.assertEqual(op['responses'][status]['headers']['Cache-Control']['schema'],{'const':'no-store'})
            if api=='API-121':self.assertEqual(op['security'],[{'experienceWorkload':['purchase-proof.read']}])
            elif api=='API-124':self.assertEqual(op['security'],[{'aiOrderStatusWorkload':['order-status.read_WITH_SIGNED_ACTOR'],'signedActor':[]}])
            else:self.assertEqual(op['security'],[{'bffSession':[]}])
            if api in {'API-037','API-040','API-044','API-238','API-240','API-241','API-242'}:self.assertTrue({'Idempotency-Key','X-CSRF-TOKEN'}.issubset({p['name'] for p in op['parameters'] if p['required']}))
            if api in {'API-040','API-044','API-238','API-240','API-241','API-242'}:self.assertIn('If-Match',{p['name'] for p in op['parameters']})
        self.assertIn('202',paths['/api/v1/checkout/attempts']['post']['responses'])
        self.assertIn('202',paths['/api/v1/orders/{id}/cancellation']['post']['responses'])
        self.assertEqual(paths['/api/v1/orders/{id}/returns']['get']['operationId'],'API-237')
        self.assertEqual(paths['/api/v1/staff/orders/{id}/returns']['get']['operationId'],'API-243')
        for route in ['/api/v1/orders/{id}/returns','/api/v1/returns/{id}','/api/v1/staff/orders/{id}/returns']:
            read=paths[route]['get'];self.assertEqual({p['name'] for p in read['parameters']}, {'id'})
            self.assertEqual(read['security'],[{'bffSession':[]}])
    def test_frozen_values_are_closed_and_private_proof_is_boolean_only(self):
        s=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())['components']['schemas']
        for name in ['CheckoutInput','OrderDTO','OrderLineDTO','OrderTotals','ProofDTO','PurchaseProofInput','OrderAddressSnapshot']:self.assertFalse(s[name]['additionalProperties'])
        self.assertEqual(set(s['ProofDTO']['properties']),{'eligible','reasonCode','revision'})
        self.assertEqual(s['OrderDTO']['properties']['lines']['maxItems'],100)
        self.assertFalse(s['OrderCancellation']['additionalProperties'])
        self.assertEqual(s['OrderDTO']['properties']['cancellationAllowed'],{'type':'boolean'})
        self.assertIn('refundId',s['OrderCancellation']['properties'])
        self.assertEqual(s['OrderPage']['properties']['items']['maxItems'],100)
        self.assertNotIn('id',s['OrderAddressSnapshot']['properties']);self.assertNotIn('version',s['OrderAddressSnapshot']['properties']);self.assertNotIn('merchantAccount',s['CheckoutPayment']['properties'])
        self.assertEqual(s['CheckoutInput']['properties']['paymentMethod'],{'type':'string','enum':['HOSTED','CASH_ON_DELIVERY']})
        self.assertFalse(s['AiOrderStatusInput']['additionalProperties']);self.assertFalse(s['AiOrderStatus']['additionalProperties'])
        self.assertEqual(set(s['AiOrderStatus']['properties']),{'orderNumber','status','fulfillmentStatus','trackingUrl'})
        for name in ['ReturnDTO','ReturnLineDTO','OrderReturnsDTO','ReturnRequestInput','ReturnReviewInput','ReturnReceiptInput','ReturnRefundInput']:self.assertFalse(s[name]['additionalProperties'],name)
        self.assertEqual(set(s['ReturnDTO']['properties']),{'id','orderId','orderLineId','productName','quantity','reason','policyRevision','state','decisionReason','instructions','receiptReference','restock','receivedAt','refundId','refundState','version','createdAt'})
        self.assertEqual(set(s['OrderReturnsDTO']['properties']),{'orderId','orderVersion','delivered','lines','requests','refundableAmount','paystackRefundAvailable'})
        self.assertEqual(set(s['ReturnReceiptInput']['properties']),{'receiptReference','restock','reason'})
        self.assertEqual(s['OrderReturnsDTO']['properties']['requests']['maxItems'],100)
