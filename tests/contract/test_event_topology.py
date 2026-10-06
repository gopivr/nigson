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
  policies=json.loads((ROOT/'infrastructure/terraform/modules/messaging/filters.json').read_text())
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
 def test_local_provider_mocks_have_no_published_ports(self):
  # Compose materialization is checked by local-config-check; this protects the checked-in surface.
  import subprocess
  result=subprocess.run(['docker','compose','--env-file','.env.example','--profile','*','config','--format','json'],cwd=ROOT,check=True,capture_output=True,text=True)
  config=json.loads(result.stdout)
  for provider in ('paystack','fedex','twilio'):
   service=config['services'][provider+'-mock']
   self.assertFalse(service.get('ports'))
   self.assertIn('--no-request-journal',service['command'])
   self.assertIn('@sha256:',service['image'])
