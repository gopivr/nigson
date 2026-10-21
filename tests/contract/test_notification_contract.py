import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class NotificationContracts(unittest.TestCase):
    def test_minimum_context_closed_receipt_and_private_scopes(self):
        s=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());schemas=s['components']['schemas']
        self.assertEqual(set(schemas['NotificationContext']['properties']),{'orderNumber','recipient','total','currency','trackingUrl'})
        self.assertFalse(schemas['NotificationDelivery']['additionalProperties'])
        self.assertEqual(schemas['NotificationDelivery']['properties']['state']['enum'],['SENT','FAILED','SUPPRESSED','UNKNOWN'])
        self.assertEqual(s['paths']['/internal/v1/notification-deliveries']['post']['security'],[{'integrationWorkload':['notification.write']}])
    def test_signed_admin_proxy_has_closed_schema_and_optional_create_version(self):
        s=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());op=s['paths']['/api/v1/staff/notification-templates']['post']
        self.assertEqual({p['name'] for p in op['parameters'] if p['required']},{'Idempotency-Key','X-CSRF-TOKEN'})
        self.assertFalse(s['components']['schemas']['NotificationTemplate']['additionalProperties'])
        registry={r['api']:r for r in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())}
        self.assertEqual(registry['API-142']['scope'],'templates.write_WITH_SIGNED_ADMIN')
        self.assertEqual(registry['API-122']['scope'],'notification.read')
    def test_twilio_status_callback_has_fixed_signature_and_private_receipt_contract(self):
        commerce=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());callback=commerce['paths']['/api/v1/webhooks/notifications/twilio/status']['post']
        self.assertEqual(callback['operationId'],'API-188');self.assertEqual(callback['security'],[{'twilioSignature':[]}])
        self.assertEqual({p['name'] for p in callback['parameters']},{'jobId','X-Twilio-Signature'})
        self.assertIn('application/x-www-form-urlencoded',callback['requestBody']['content'])
        self.assertEqual(commerce['components']['schemas']['TwilioStatusAcknowledged']['properties'],{'received':{'type':'boolean'}})
        integration=json.loads((ROOT/'contracts/openapi/integration.yaml').read_text());receipt=integration['paths']['/internal/v1/notification-provider-receipts']['post']
        self.assertEqual(receipt['operationId'],'API-189');self.assertEqual(receipt['security'],[{'workload':[]}])
        self.assertIn('notification.feedback.write',receipt['description'])
        self.assertFalse(integration['components']['schemas']['NotificationReceiptInput']['additionalProperties'])
        self.assertEqual(integration['components']['schemas']['NotificationReceiptAcknowledged']['properties']['acknowledged']['const'],True)
        registry={r['api']:r for r in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())}
        self.assertEqual((registry['API-189']['owner'],registry['API-189']['client'],registry['API-189']['scope']),('integration','nigson-commerce','notification.feedback.write'))

    def test_api142_is_exact_private_signed_admin_template_registration(self):
        integration=json.loads((ROOT/'contracts/openapi/integration.yaml').read_text())
        operation=integration['paths']['/internal/v1/templates']['post']
        self.assertEqual(operation['operationId'],'API-142')
        self.assertEqual(operation['security'],[{'workload':['templates.write_WITH_SIGNED_ADMIN'],'signedActor':[]}])
        self.assertEqual({p['name'] for p in operation['parameters'] if p['required']},{'Idempotency-Key'})
        self.assertIn('If-Match',{p['name'] for p in operation['parameters']})
        body=integration['components']['schemas']['NotificationTemplateInput']
        self.assertFalse(body['additionalProperties'])
        self.assertEqual(set(body['required']),{'code','revision','locale','channel','classification','body','variablesSchema','active'})
        self.assertEqual(set(integration['components']['schemas']['NotificationTemplateView']['properties']),{'id','code','revision','active'})
        registry={r['api']:r for r in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())}
        self.assertEqual((registry['API-142']['owner'],registry['API-142']['client'],registry['API-142']['scope']),('integration','nigson-commerce','templates.write_WITH_SIGNED_ADMIN'))
