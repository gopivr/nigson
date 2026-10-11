import json
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[2]
class PaymentContracts(unittest.TestCase):
    def test_callback_private_and_finance_contracts_have_distinct_trust(self):
        spec = json.loads((ROOT / 'contracts/openapi/commerce.yaml').read_text())
        paths = spec['paths']
        callback = paths['/api/v1/webhooks/payments/paystack']['post']
        self.assertEqual(callback['operationId'], 'API-042')
        self.assertEqual(callback['security'], [{'paystackSignature': []}])
        self.assertEqual(callback['x-raw-body-max-bytes'], 262144)
        self.assertNotIn('X-CSRF-TOKEN', {p['name'] for p in callback['parameters']})
        private = paths['/internal/v1/payments/reconciliation']['post']
        self.assertEqual(private['security'], [{'paymentIntegrationWorkload': ['payment.reconcile']}])
        self.assertIn('202', private['responses'])
        for route in ['/api/v1/staff/orders/{id}/refunds', '/api/v1/staff/refunds/{id}/approval']:
            op = paths[route]['post']
            self.assertEqual({p['name'] for p in op['parameters'] if p['required']}, {'id', 'Idempotency-Key', 'If-Match', 'X-CSRF-TOKEN'})
            self.assertEqual(op['security'], [{'bffSession': []}])
    def test_refund_detail_and_immutable_inputs_are_closed_and_bounded(self):
        schemas = json.loads((ROOT / 'contracts/openapi/commerce.yaml').read_text())['components']['schemas']
        self.assertEqual(set(schemas['RefundDTO']['properties']), {'id', 'state', 'amount', 'currency', 'version'})
        self.assertEqual(set(schemas['RefundDetailDTO']['properties']), set(schemas['RefundDTO']['properties']) | {'providerReference'})
        self.assertEqual(schemas['RefundInput']['properties']['lines']['maxItems'], 100)
        for name in ['PaymentDTO', 'RefundDTO', 'RefundDetailDTO', 'RefundInput', 'RefundLineInput', 'RefundApprovalInput', 'PaymentReconciliationInput', 'PaymentReconciliationDTO']:
            self.assertFalse(schemas[name]['additionalProperties'])
        self.assertNotIn('lines', schemas['RefundInput']['required'])
        self.assertEqual(set(schemas['PaymentDTO']['properties']), {'orderId', 'state', 'settled', 'reference', 'checkoutUrl'})
