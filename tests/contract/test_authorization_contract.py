import json
from pathlib import Path
import subprocess
import sys
import unittest
ROOT = Path(__file__).resolve().parents[2]
class AuthorizationContracts(unittest.TestCase):
    def test_reviewed_generation_and_exact_implemented_routes(self):
        subprocess.run([sys.executable, 'scripts/contracts/authorization.py', '--check'], cwd=ROOT, check=True)
        spec = json.loads((ROOT / 'contracts/openapi/commerce.yaml').read_text())
        self.assertEqual(len(spec['paths']), 110)
        self.assertEqual({op['operationId'] for path in spec['paths'].values() for op in path.values()}, {f'API-{n:03}' for n in range(1,26)} | {'API-062','API-063','API-064','API-065','API-066','API-067','API-161','API-111', 'API-112', 'API-113', 'API-117', 'API-132', 'API-135', 'API-149', 'API-163', 'API-164', 'API-122', 'API-133', 'API-165', 'API-045', 'API-046', 'API-170', 'API-029', 'API-031', 'API-162', 'API-169', 'API-026', 'API-027', 'API-028', 'API-036', 'API-150', 'API-151', 'API-032', 'API-033', 'API-034', 'API-035', 'API-047', 'API-048', 'API-049', 'API-050', 'API-171', 'API-174', 'API-037', 'API-038', 'API-039', 'API-040', 'API-043', 'API-044', 'API-121', 'API-124', 'API-041', 'API-042', 'API-052', 'API-053', 'API-054', 'API-118', 'API-181', 'API-119', 'API-120', 'API-051', 'API-160', 'API-191', 'API-192', 'API-197', 'API-199', 'API-200', 'API-183', 'API-184', 'API-185', 'API-186', 'API-188', 'API-068','API-069','API-070','API-071','API-072','API-073','API-074','API-123','API-173','API-195','API-206','API-055','API-056','API-057','API-058','API-059','API-060','API-061','API-152','API-153','API-154','API-155', 'API-134','API-159','API-075','API-030'})
        organizations = spec['paths']['/api/v1/me/organizations']['get']
        self.assertEqual(organizations['operationId'], 'API-066')
        self.assertEqual(organizations['security'], [{'bffSession': []}])
        self.assertFalse(spec['components']['schemas']['OrganizationMembershipDTO']['additionalProperties'])
        entitlement = spec['paths']['/internal/v1/distributor-entitlements']['post']
        self.assertEqual(entitlement['operationId'], 'API-117')
        self.assertEqual({p['name'] for p in entitlement['parameters'] if p['required']}, {'If-Match', 'Idempotency-Key'})
        self.assertIn('412', entitlement['responses'])
        self.assertFalse(spec['components']['schemas']['DistributorEntitlementCommand']['additionalProperties'])
        self.assertEqual(spec['paths']['/api/v1/me/rewards']['get']['operationId'],'API-062')
        self.assertEqual(spec['paths']['/api/v1/me/referral-code']['get']['operationId'],'API-063')
        self.assertEqual(spec['paths']['/api/v1/me/referral-code']['post']['operationId'],'API-161')
        self.assertEqual(spec['paths']['/api/v1/me/referral-attribution']['post']['operationId'],'API-064')
        self.assertFalse(spec['components']['schemas']['ReferralAttributionInput']['additionalProperties'])
        promotions = {'API-055','API-056','API-057','API-058','API-059','API-060','API-061','API-152','API-153','API-154','API-155'}
        promotion_ops = {op['operationId']: op for path in spec['paths'].values() for op in path.values() if op['operationId'] in promotions}
        self.assertEqual(set(promotion_ops), promotions)
        for name in ('PromotionRuleInput','PromotionRuleRevisionInput','PromotionActivation','CouponCreate','CouponToggle','WheelCampaignCreate','WheelCampaignToggle','WheelDrawInput'):
            self.assertFalse(spec['components']['schemas'][name]['additionalProperties'])
        self.assertEqual({p['name'] for p in promotion_ops['API-153']['parameters'] if p['required']}, {'id','If-Match','Idempotency-Key','X-CSRF-TOKEN'})
        self.assertIn('approved active commerce.feature_configuration', promotion_ops['API-153']['description'])
        self.assertIn('UTC per-party daily slot', promotion_ops['API-061']['description'])
        staff = spec['paths']['/api/v1/staff/leads/{id}/messages']['post']
        self.assertEqual(staff['operationId'], 'API-192')
        self.assertEqual({p['name'] for p in staff['parameters'] if p['required']}, {'id', 'Idempotency-Key', 'X-CSRF-TOKEN'})
        put = spec['paths']['/api/v1/staff/users/{id}/roles']['put']
        self.assertEqual({p['name'] for p in put['parameters'] if p['required']}, {'id', 'If-Match', 'Idempotency-Key', 'X-CSRF-TOKEN'})
        self.assertIn('412', put['responses'])
        self.assertFalse(spec['components']['schemas']['StaffRoleChange']['additionalProperties'])
        self.assertEqual(set(spec['components']['schemas']['StaffDTO']['required']), {'id', 'displayName', 'email', 'roles', 'version'})
        self.assertNotIn('CUSTOMER', spec['components']['schemas']['Role']['enum'])

    def test_api_134_is_exact_bff_admin_read_contract(self):
        spec = json.loads((ROOT / 'contracts/openapi/commerce.yaml').read_text())
        operation = spec['paths']['/api/v1/commerce/staff/audit-records']['get']
        self.assertEqual(operation['operationId'], 'API-134')
        self.assertEqual(operation['security'], [{'bffSession': []}])
        self.assertEqual({p['name'] for p in operation['parameters']}, {'resourceId', 'action', 'from', 'to', 'page', 'size', 'sort'})
        self.assertEqual(operation['responses']['200']['headers']['Cache-Control']['schema'], {'const': 'no-store'})
        self.assertEqual(set(spec['components']['schemas']['AuditDTO']['required']), {'id', 'actor', 'action', 'resourceType', 'resourceId', 'occurredAt', 'reason', 'oldValue', 'newValue'})
        self.assertIn('Current Commerce ADMIN only', operation['description'])
        self.assertIn('does not mutate audit history', operation['description'])
