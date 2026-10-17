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
        self.assertEqual({op['operationId'] for path in spec['paths'].values() for op in path.values()},{'API-127','API-175','API-187','API-190','API-201','API-202','API-203','API-204'})
        inbound=spec['paths']['/api/v1/webhooks/notifications/twilio/whatsapp']['post']
        self.assertEqual(inbound['security'],[])
        self.assertIn('X-Twilio-Signature',{p['name'] for p in inbound['parameters']})
        self.assertEqual(inbound['requestBody']['content']['application/x-www-form-urlencoded']['schema']['required'],['AccountSid','MessageSid','From','To','Body'])
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
        self.assertFalse({'phone','rendered_code','rendered_body','destination_ciphertext'}&fields)
        self.assertEqual(table['owner'],'integration')
        self.assertEqual(table['phase'],1)
    def test_new_dispatch_migration_preserves_applied_history_and_blocks_populated_rollback(self):
        change=json.loads((ROOT/'services/integration/src/main/resources/db/changelog/024-bp1-notification-dispatch.yaml').read_text())['databaseChangeLog'][0]['changeSet']
        self.assertEqual(change['id'],'integration-20261017-msg-001')
        rollback=(ROOT/'services/integration/src/main/resources/db/changelog/024-bp1-notification-dispatch-rollback.sql').read_text()
        self.assertIn('Populated table integration.notification_dispatch',rollback)
        self.assertIn('RAISE EXCEPTION',rollback)
