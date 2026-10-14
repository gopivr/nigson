import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
class FulfillmentContracts(unittest.TestCase):
    def test_exact_six_owned_mutations(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());paths=spec['paths']
        expected={'/api/v1/staff/delivery-rules':('post','API-171','201'),'/api/v1/staff/delivery-rules/{id}':('put','API-047','200'),'/api/v1/staff/orders/{id}/shipments':('post','API-048','201'),'/api/v1/staff/shipments/{id}/dispatch':('post','API-049','200'),'/api/v1/staff/shipments/{id}/delivery':('post','API-050','200'),'/api/v1/staff/orders/{id}/stock-returns':('post','API-174','201')}
        for route,(method,api,status) in expected.items():
            op=paths[route][method];self.assertEqual(op['operationId'],api);self.assertEqual(op['security'],[{'bffSession':[]}]);self.assertIn(status,op['responses']);self.assertEqual(op['responses'][status]['headers']['Cache-Control']['schema'],{'const':'no-store'})
            self.assertTrue({'Idempotency-Key','X-CSRF-TOKEN'}.issubset({p['name'] for p in op['parameters'] if p.get('required')}))
            if api in {'API-047','API-049','API-050'}:self.assertIn('If-Match',{p['name'] for p in op['parameters'] if p.get('required')})
    def test_closed_bounded_commands_and_minimal_shipping_responses(self):
        s=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())['components']['schemas']
        for name in ['DeliveryRuleInput','ShipmentInput','StockReturnInput','ShipmentReason','ShipmentDelivery']:self.assertFalse(s[name]['additionalProperties'])
        for name in ['ShipmentInput','StockReturnInput']:self.assertEqual(s[name]['properties']['lines']['maxItems'],100)
        self.assertEqual(s['DeliveryRuleInput']['properties']['countryCode'],{'const':'NG'})
        self.assertNotIn('labelAssetId',s['ShipmentDTO']['properties']);self.assertNotIn('packageSnapshot',s['ShipmentDTO']['properties']);self.assertNotIn('balanceId',s['ShipmentLineDTO']['properties'])
        self.assertEqual(set(s['StockReturnDTO']['properties']),{'returnId','orderId','lines'})

    def test_customer_carrier_quote_uses_closed_input_and_versioned_csrf(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());op=spec['paths']['/api/v1/carts/{id}/delivery-quotes']['post'];schemas=spec['components']['schemas']
        self.assertEqual(op['operationId'],'API-181');self.assertEqual(op['security'],[{'bffSession':[]}])
        self.assertEqual({p['name'] for p in op['parameters'] if p.get('required')},{'id','Idempotency-Key','X-CSRF-TOKEN','If-Match'})
        self.assertEqual(set(schemas['DeliveryQuoteInput']['properties']),{'addressId','serviceCode'});self.assertFalse(schemas['DeliveryQuoteInput']['additionalProperties'])
        self.assertEqual(schemas['DeliveryQuoteInput']['required'],['addressId']);self.assertEqual(schemas['DeliveryQuoteDTO']['properties']['currency']['const'],'NGN');self.assertEqual(schemas['DeliveryQuoteDTO']['properties']['mode']['enum'],['MOCK','SANDBOX','LIVE'])
        self.assertIn('201',op['responses']);self.assertIn('503',op['responses']);self.assertNotIn('ETag',op['responses']['201']['headers'])

    def test_private_shipment_context_is_frozen_bounded_and_scoped_at_launch(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());op=spec['paths']['/internal/v1/shipments/{id}/provider-context']['get'];schemas=spec['components']['schemas']
        self.assertEqual(op['operationId'],'API-119');self.assertEqual(op['security'],[{'carrierIntegrationWorkload':['logistics.read']}]);self.assertNotIn('requestBody',op)
        self.assertEqual(op['responses']['200']['headers']['Cache-Control']['schema'],{'const':'no-store'})
        context=schemas['ShipmentProviderContext'];self.assertFalse(context['additionalProperties']);self.assertEqual(context['properties']['packages']['maxItems'],100)
        self.assertEqual(context['properties']['address'],{'$ref':'#/components/schemas/OrderAddressSnapshot'})
        self.assertTrue({'id','version','organizationId'}.isdisjoint(schemas['OrderAddressSnapshot']['properties']))
        route=next(r for r in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text()) if r['api']=='API-119')
        self.assertEqual((route['client'],route['scope'],route['phase']),('nigson-integration','logistics.read',1))

    def test_private_provider_result_requires_trusted_closed_source_command(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());op=spec['paths']['/internal/v1/shipments/{id}/provider-result']['post'];schema=spec['components']['schemas']['ShipmentProviderResult']
        self.assertEqual(op['operationId'],'API-120');self.assertEqual(op['security'],[{'carrierIntegrationWorkload':['logistics.write']}]);self.assertFalse(schema['additionalProperties'])
        self.assertEqual(set(schema['required']),{'commandId','provider','reference','status','occurredAt','mode'});self.assertTrue({'fee','account','dispatchedAt'}.isdisjoint(schema['properties']))
        self.assertEqual(schema['properties']['bookingState']['enum'],['BOOKED','UNKNOWN','FAILED']);self.assertEqual(schema['properties']['labelChecksum']['pattern'],'^[a-f0-9]{64}$')
        route=next(r for r in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text()) if r['api']=='API-120');self.assertEqual((route['client'],route['scope'],route['phase']),('nigson-integration','logistics.write',1))
