"""Verify phase-specific exact event/producer routing against the authoritative registry."""
import importlib.util
import json
from pathlib import Path
import unittest
ROOT=Path(__file__).resolve().parents[2]
spec=importlib.util.spec_from_file_location('topology',ROOT/'scripts/platform/topology.py')
topology=importlib.util.module_from_spec(spec);spec.loader.exec_module(topology)

class EventTopologyTests(unittest.TestCase):
 def test_all_phases_match_terraform_and_closed_registry(self):
  registry=json.loads((ROOT/'contracts/events/registry.json').read_text())
  self.assertEqual(28,len(registry))
  self.assertEqual(registry,json.loads((ROOT/'shared/common-events/src/main/resources/events/registry.json').read_text()))
  self.assertEqual(registry,json.loads((ROOT/'services/ai/src/nigson_ai/platform/registry.json').read_text()))
  policies=json.loads((ROOT/'infrastructure/terraform/aws/modules/messaging/filters.json').read_text())
  for phase in range(1,6):
   expected={topology.QUEUES[owner]:topology.filters(owner,phase) for owner in topology.QUEUES if owner!='ai' or phase==5}
   self.assertEqual(expected,policies[str(phase)])
   for owner in topology.QUEUES:
    if owner=='ai' and phase!=5:continue
    allowed={(branch['producer'][0],event) for branch in expected[topology.QUEUES[owner]].get('$or',[expected[topology.QUEUES[owner]]]) if 'producer' in branch for event in branch['event_type']}
    exact={(producer,name) for name,contract in registry.items() if owner in contract['consumers'] and contract['phase']<=phase for producer in contract['producers']}
    self.assertEqual(exact,allowed)
    for _,name in exact:
     schema=registry[name]['schema'];self.assertFalse(schema['additionalProperties']);self.assertFalse(schema['properties']['payload']['additionalProperties'])
 def test_application_compose_excludes_provider_simulators_and_uses_live_defaults(self):
  import subprocess
  result=subprocess.run(['docker','compose','--env-file','.env.example','--profile','*','config','--format','json'],cwd=ROOT,check=True,capture_output=True,text=True)
  config=json.loads(result.stdout)
  for provider in ('paystack','twilio'):
   self.assertNotIn(provider+'-mock',config['services'])
  commerce=config['services']['commerce']['environment']
  integration=config['services']['integration']['environment']
  self.assertEqual('LIVE',commerce['PAYSTACK_MODE'])
  self.assertEqual('https://api.paystack.co',commerce['PAYSTACK_BASE_URL'])
  self.assertEqual('LIVE',integration['TWILIO_MODE'])
  self.assertEqual('https://api.twilio.com',integration['TWILIO_BASE_URL'])
  self.assertEqual('LIVE',integration['EMAIL_MODE'])
  self.assertNotEqual('mailpit',integration['SMTP_HOST'])
