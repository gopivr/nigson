import json
from pathlib import Path
import unittest
ROOT = Path(__file__).resolve().parents[2]
class ProviderContracts(unittest.TestCase):
    def test_closed_job_shapes_and_versioned_signed_retry(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        job=spec['components']['schemas']['JobDTO']
        self.assertFalse(job['additionalProperties'])
        self.assertEqual(set(job['properties']),{'id','state','attemptCount','nextAttemptAt','lastErrorCode'})
        self.assertEqual(set(spec['components']['schemas']['JobRetry']['properties']),{'reason'})
        op=spec['paths']['/api/v1/staff/integration-jobs/{id}/retry']['post']
        self.assertEqual({p['name'] for p in op['parameters'] if p['required']},{'id','If-Match','Idempotency-Key','X-CSRF-TOKEN'})
        self.assertIn('202',op['responses'])
        self.assertIn('ETag',op['responses']['202']['headers'])
    def test_read_and_retry_follow_specific_compiled_scopes(self):
        routes=json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())
        registry={r['api']:r for r in routes}
        self.assertEqual(registry['API-140']['scope'],'jobs.read')
        self.assertEqual(registry['API-141']['scope'],'jobs.retry_WITH_SIGNED_STAFF')
        self.assertEqual(registry['API-141']['owner'],'integration')

    def test_private_integration_job_contract_preserves_safe_shapes_and_exact_scopes(self):
        spec=json.loads((ROOT/'contracts/openapi/integration.yaml').read_text())
        self.assertEqual(set(spec['paths']),{'/internal/v1/jobs','/internal/v1/jobs/{id}/retry','/internal/v1/carrier/quotes','/internal/v1/carrier/shipments/{id}/reconciliation','/internal/v1/carrier/cancellations'})
        self.assertEqual(set(spec['components']['schemas']['JobDTO']['properties']),{'id','state','attemptCount','nextAttemptAt','lastErrorCode'})
        self.assertFalse(spec['components']['schemas']['JobDTO']['additionalProperties'])
        get=spec['paths']['/internal/v1/jobs']['get'];retry=spec['paths']['/internal/v1/jobs/{id}/retry']['post']
        self.assertEqual(get['operationId'],'API-140');self.assertEqual(retry['operationId'],'API-141')
        self.assertEqual(get['security'],[{'workload':[]}])
        self.assertEqual(retry['security'],[{'workload':[],'signedActor':[]}])
        self.assertIn('ETag',retry['responses']['202']['headers'])
        self.assertIn('503',retry['responses'])
        self.assertEqual({p['name'] for p in retry['parameters'] if p['required']},{'id','If-Match','Idempotency-Key','X-Correlation-ID'})

    def test_private_integration_generator_emits_only_reviewed_implemented_routes(self):
        import subprocess
        result=subprocess.run(['python3',str(ROOT/'scripts/contracts/integration.py'),'--check'],cwd=ROOT,capture_output=True,text=True,check=True)
        self.assertIn('PASS:',result.stdout)

    def test_private_carrier_quote_is_closed_signed_ngn_server_protocol(self):
        spec=json.loads((ROOT/'contracts/openapi/integration.yaml').read_text())
        op=spec['paths']['/internal/v1/carrier/quotes']['post']
        self.assertEqual(op['operationId'],'API-182')
        self.assertEqual(op['security'],[{'workload':[],'signedActor':[]}])
        self.assertEqual(op['responses']['200']['headers']['X-Nigson-Carrier-Mode']['schema']['enum'],['MOCK','SANDBOX','LIVE'])
        schemas=spec['components']['schemas'];request=schemas['CarrierQuoteInput']
        self.assertFalse(request['additionalProperties'])
        self.assertEqual(set(request['properties']),{'commandId','cartId','market','currency','shipper','destination','packages','serviceCode'})
        self.assertEqual(request['properties']['packages']['maxItems'],100)
        self.assertNotIn('serviceCode',request['required'])
        self.assertEqual(schemas['CarrierQuoteDTO']['properties']['currency']['const'],'NGN')
        self.assertEqual(schemas['CarrierAddress']['properties']['countryCode']['const'],'NG')
        registry={r['api']:r for r in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())}
        self.assertEqual(registry['API-182']['scope'],'carrier.quote_WITH_SIGNED_ACTOR')

    def test_carrier_reconciliation_keeps_exact_scoped_closed_owner_contract(self):
        public=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        private=json.loads((ROOT/'contracts/openapi/integration.yaml').read_text())
        operation=public['paths']['/api/v1/staff/shipments/{id}/booking-reconciliation']['post']
        self.assertEqual(operation['operationId'],'API-191')
        self.assertEqual({p['name'] for p in operation['parameters'] if p['required']},{'id','If-Match','Idempotency-Key','X-CSRF-TOKEN'})
        self.assertIn('202',operation['responses'])
        self.assertEqual(set(public['components']['schemas']['CarrierReconciliationDTO']['properties']),{'shipmentId','state','jobId'})
        op=private['paths']['/internal/v1/carrier/shipments/{id}/reconciliation']['post']
        self.assertEqual(op['operationId'],'API-196')
        self.assertEqual(op['security'],[{'workload':[],'signedActor':[]}])
        self.assertFalse(private['components']['schemas']['CarrierReconciliationInput']['additionalProperties'])
        self.assertEqual(set(private['components']['schemas']['CarrierReconciliationInput']['properties']),{'bookingRequestId','mode','reason'})
        registry={r['api']:r for r in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())}
        self.assertEqual(registry['API-196']['scope'],'carrier.reconcile_WITH_SIGNED_STAFF')
        self.assertEqual(registry['API-196']['client'],'nigson-commerce')
        self.assertEqual(registry['API-196']['phase'],1)
    def test_cancellation_is_closed_and_uses_pure_owner_workloads(self):
        public=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        private=json.loads((ROOT/'contracts/openapi/integration.yaml').read_text())
        path=public['paths']['/api/v1/staff/shipments/{id}/carrier-cancellation']
        self.assertEqual(path['post']['operationId'],'API-197')
        self.assertEqual(path['get']['operationId'],'API-200')
        self.assertEqual(public['paths']['/internal/v1/carrier/cancellation-results']['post']['operationId'],'API-199')
        self.assertEqual(private['paths']['/internal/v1/carrier/cancellations']['post']['operationId'],'API-198')
        for spec in (public,private):
            for name,fields in [('CarrierCancellationCommand',{'cancellationId','shipmentId','bookingRequestId','reference','mode','requestedAt'}),('CarrierCancellationReceipt',{'commandId','cancellationId','shipmentId','bookingRequestId','reference','mode','state','occurredAt','reasonCode'}),('CarrierCancellationDTO',{'id','shipmentId','state','version','shipmentVersion','errorCode'})]:
                schema=spec['components']['schemas'][name]
                self.assertFalse(schema['additionalProperties'])
                self.assertEqual(set(schema['properties']),fields)
                self.assertEqual(set(schema['required']),fields)
        registry={r['api']:r for r in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())}
        self.assertEqual((registry['API-198']['scope'],registry['API-198']['client'],registry['API-198']['phase']),('carrier.cancel','nigson-commerce',1))
        self.assertEqual((registry['API-199']['scope'],registry['API-199']['client'],registry['API-199']['phase']),('logistics.write','nigson-integration',1))
        self.assertEqual(private['paths']['/internal/v1/carrier/cancellations']['post']['security'],[{'workload':[]}])

    def test_account_identity_is_private_metadata_and_required_creation_context(self):
        private=json.loads((ROOT/'contracts/openapi/integration.yaml').read_text())
        public=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text())
        header=private['paths']['/internal/v1/carrier/quotes']['post']['responses']['200']['headers']['X-Nigson-Carrier-Account-SHA256']
        self.assertEqual(header['schema'],{'type':'string','pattern':'^[a-f0-9]{64}$'})
        context=public['components']['schemas']['ShipmentProviderContext']
        self.assertIn('accountHash',context['required'])
        self.assertEqual(context['properties']['accountHash'],{'type':'string','pattern':'^[a-f0-9]{64}$'})
        for name in ('DeliveryQuoteDTO','ShipmentDTO','DeliveryQuoteInput','ShipmentInput'):
            self.assertNotIn('accountHash',public['components']['schemas'][name]['properties'])
        self.assertNotIn('accountHash',private['components']['schemas']['CarrierQuoteInput']['properties'])
        self.assertNotIn('accountHash',private['components']['schemas']['CarrierQuoteDTO']['properties'])
