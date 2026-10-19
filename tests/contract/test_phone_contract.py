"""Phone proof and BFF/private trust remain closed and owner scoped."""
import json
import subprocess
import sys
import unittest
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
class PhoneContracts(unittest.TestCase):
    def test_generated_experience_routes_and_exact_scopes(self):
        subprocess.run([sys.executable,'scripts/contracts/phone.py','--check'],cwd=ROOT,check=True)
        spec=json.loads((ROOT/'contracts/openapi/experience.yaml').read_text())
        self.assertEqual({op['operationId'] for path in spec['paths'].values() for op in path.values()},{'API-076','API-077','API-078','API-079','API-080','API-081','API-082','API-083','API-084','API-085','API-086','API-088','API-092','API-093','API-094','API-095','API-097','API-098','API-099','API-100','API-101','API-102','API-103','API-104','API-105','API-106','API-107','API-108','API-109','API-110','API-127','API-128','API-138','API-156','API-157','API-158','API-172','API-175','API-176','API-177','API-187','API-190','API-194','API-201','API-202','API-203','API-204','API-205','API-130','API-131','API-125','API-126'})
        inbound=spec['paths']['/api/v1/webhooks/notifications/twilio/whatsapp']['post']
        self.assertEqual(inbound['security'],[])
        self.assertIn('X-Twilio-Signature',{p['name'] for p in inbound['parameters']})
        self.assertEqual(inbound['requestBody']['content']['application/x-www-form-urlencoded']['schema']['required'],['AccountSid','MessageSid','From','To','Body'])
        context=spec['paths']['/internal/v1/leads/{id}/messaging-context']['get']
        self.assertEqual(context['security'],[{'workload':['notification.read_WITH_SIGNED_ACTOR'],'signedActor':[]}])
        self.assertEqual(set(spec['components']['schemas']['LeadMessagingContext']['properties']),{'phone','channel','destinationHash','eligible','basis','approvedTemplateCodes'})
        suppression=spec['paths']['/internal/v1/notification-suppression-checks']['post']
        self.assertEqual(suppression['security'],[{'workload':['notification.read']}])
        self.assertEqual(set(spec['components']['schemas']['PhoneSuppressionCheck']['properties']),{'destinationHash','channel'})
        report=spec['paths']['/api/v1/staff/reports/engagement']['get']
        self.assertEqual(report['operationId'],'API-110')
        self.assertEqual(report['security'],[{'workload':['report.read_WITH_SIGNED_ANALYST'],'signedActor':[]}])
        self.assertEqual({p['name'] for p in report['parameters'] if p['required']},{'from','to'})
        report_scope={row['api']:row for row in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())}['API-110']
        self.assertEqual((report_scope['method'],report_scope['owner'],report_scope['client'],report_scope['scope']),('GET','experience','nigson-commerce','report.read_WITH_SIGNED_ANALYST'))
        distributor_scopes={row['api']:row['scope'] for row in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text()) if row['api'] in {'API-092','API-093','API-094','API-095','API-172','API-177'}}
        self.assertEqual(set(distributor_scopes),{'API-092','API-093','API-094','API-095','API-172','API-177'})
        self.assertTrue(distributor_scopes['API-092'].endswith('_WITH_SIGNED_CUSTOMER'))
        self.assertTrue(all(distributor_scopes[k].endswith('_WITH_SIGNED_ACTOR') for k in {'API-094','API-095','API-172','API-177'}))
        self.assertIn('/api/v1/distributor-applications',spec['paths'])
        lead_create=spec['paths']['/api/v1/quote-requests']['post']
        self.assertEqual(lead_create['operationId'],'API-088')
        self.assertEqual(lead_create['security'],[{'workload':['lead.quote.create_WITH_SIGNED_CUSTOMER'],'signedActor':[]}])
        self.assertEqual(spec['paths']['/internal/v1/leads/{id}/quote-context']['get']['security'],[{'workload':['quote.read']}])
        self.assertEqual(spec['paths']['/internal/v1/leads/{id}/quote-association']['post']['security'],[{'workload':['quote.write']}])
    def test_inbound_retention_and_phone_only_migrations_are_additive_and_guarded(self):
        release=json.loads((ROOT/'services/experience/src/main/resources/db/changelog/releases/BP1.yaml').read_text())['databaseChangeLog']
        self.assertTrue(any(row['include']['file']=='../023-msg-001-lead-email-nullable.yaml' for row in release))
        self.assertTrue(any(row['include']['file']=='../024-msg-001-inbound-retention.yaml' for row in release))
        rollback=(ROOT/'services/experience/src/main/resources/db/changelog/024-msg-001-inbound-retention-rollback.sql').read_text()
        self.assertIn('sender_ciphertext IS NULL',rollback);self.assertIn('RAISE EXCEPTION',rollback)
        scopes={row['api']:row for row in json.loads((ROOT/'contracts/security/workload-scopes.json').read_text())}
        for number in range(201,205):
            self.assertEqual(scopes[f'API-{number}']['owner'],'experience')
            self.assertEqual(scopes[f'API-{number}']['client'],'nigson-commerce')
            self.assertTrue(scopes[f'API-{number}']['scope'].endswith('_WITH_SIGNED_CUSTOMER'))
        self.assertEqual(scopes['API-205']['owner'],'experience')
        self.assertEqual(scopes['API-205']['client'],'nigson-integration')
        self.assertEqual(scopes['API-205']['scope'],'notification.read')
        self.assertEqual(scopes['API-193']['client'],'nigson-commerce')
    def test_public_masked_dto_and_explicit_channel_purpose(self):
        spec=json.loads((ROOT/'contracts/openapi/commerce.yaml').read_text());schemas=spec['components']['schemas']
        self.assertEqual(set(schemas['PhoneDestination']['properties']),{'id','channel','maskedAddress','status','version'})
        self.assertFalse(schemas['PhoneDestination']['additionalProperties'])
        self.assertEqual(schemas['PhoneDestinationInput']['properties']['consentedPurposes']['items']['enum'],['ORDER_UPDATES','MARKETING','CART_RECOVERY'])
        verify=spec['paths']['/api/v1/me/communication-destinations/{id}/verification']['post']
        self.assertEqual({p['name'] for p in verify['parameters'] if p['required']},{'id','Idempotency-Key','X-CSRF-TOKEN','If-Match'})
        self.assertEqual(set(schemas['PhoneProofPrivate']['properties']),{'code','version'})

    def test_paired_dispatch_catalog_is_owned_and_contains_no_rendered_recipient(self):
        model=json.loads((ROOT/'database/schema-manifest.json').read_text())
        table=model['tables']['integration.notification_dispatch']
        fields={field['name'] for field in table['fields']}
        self.assertTrue({'template_snapshot','template_hash','account_hash','generation','source_occurred_at','stage','submitted_at','provider_message_id'}<=fields)
        self.assertTrue({'command_hash','recipient_ciphertext','variables_ciphertext','context_key_revision','context_basis'}<=fields)
        self.assertFalse({'phone','rendered_code','rendered_body','destination_ciphertext'}&fields)
        self.assertEqual(table['owner'],'integration')
        self.assertEqual(table['phase'],1)
    def test_new_dispatch_migration_preserves_applied_history_and_blocks_populated_rollback(self):
        change=json.loads((ROOT/'services/integration/src/main/resources/db/changelog/024-bp1-notification-dispatch.yaml').read_text())['databaseChangeLog'][0]['changeSet']
        self.assertEqual(change['id'],'integration-20261017-msg-001')
        rollback=(ROOT/'services/integration/src/main/resources/db/changelog/024-bp1-notification-dispatch-rollback.sql').read_text()
        self.assertIn('Populated table integration.notification_dispatch',rollback)
        self.assertIn('RAISE EXCEPTION',rollback)
        envelope=json.loads((ROOT/'services/integration/src/main/resources/db/changelog/025-msg-001-staff-reply-envelope.yaml').read_text())['databaseChangeLog'][0]['changeSet']
        self.assertEqual(envelope['id'],'integration-20261017-msg-001-envelope')
        rollback=(ROOT/'services/integration/src/main/resources/db/changelog/025-msg-001-staff-reply-envelope-rollback.sql').read_text()
        self.assertIn('staff-reply context',rollback);self.assertIn('RAISE EXCEPTION',rollback)
