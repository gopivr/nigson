"""Run the real preflight function without importing Docker discovery side effects."""
import ast
from datetime import datetime, timezone
from pathlib import Path
import re
import unittest
source=Path(__file__).with_name('dependencies.py').read_text()
node=next(n for n in ast.parse(source).body if isinstance(n,ast.FunctionDef) and n.name=='application_preflight')
scope={'OWNERS':('commerce','experience','integration'),'datetime':datetime,'timezone':timezone,'re':re}
exec(compile(ast.Module(body=[node],type_ignores=[]),'dependencies.py','exec'),scope)
class PartnerPreflightTest(unittest.TestCase):
 def check(self,phase,key=None):
  env={key:'synthetic-test-only' for key in ('NIGSON_PRIVATE_PEER_CIDRS','NIGSON_ACTOR_PUBLIC_KEYS','NIGSON_ACTOR_KMS_KEY','NIGSON_ACTOR_KEY_ID','NIGSON_ACTOR_PUBLIC_KEY','NIGSON_SESSION_KEY_REVISION','NIGSON_COMMERCE_WORKLOAD_SECRET','NIGSON_SESSION_LOCAL_KEY')}
  env.update({'NIGSON_PRODUCT_PHASE':phase,'NIGSON_SECURITY_ENABLED':'true'})
  if key is not None:env['NIGSON_PRICING_DISTRIBUTOR_ENTITLEMENT']=key
  return scope['application_preflight']({'services':{'commerce':{'environment':env}}})
 def test_bp1_keeps_partner_feature_optional(self):self.check('1')
 def test_missing_partner_configuration_is_actionable(self):
  with self.assertRaisesRegex(ValueError,'NIGSON_PRICING_DISTRIBUTOR_ENTITLEMENT'):self.check('3')
 def test_valid_business_entitlement(self):self.check('3','BUSINESS_PURCHASING')
 def test_invalid_label_and_phase(self):
  with self.assertRaisesRegex(ValueError,'NIGSON_PRICING_DISTRIBUTOR_ENTITLEMENT'):self.check('3','invalid value')
  with self.assertRaisesRegex(ValueError,'NIGSON_PRODUCT_PHASE'):self.check('bad','BUSINESS_PURCHASING')
if __name__=='__main__':unittest.main()
