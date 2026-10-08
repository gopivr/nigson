"""Detect Java/Python protocol drift against reviewed route, audit and error contracts."""
import json
from pathlib import Path
import re
import unittest
ROOT=Path(__file__).resolve().parents[2]

class ObservabilityContracts(unittest.TestCase):
 def test_closed_problem_schema_and_java_vocabulary(self):
  schema=json.loads((ROOT/'contracts/problem/problem.schema.json').read_text())
  codes=json.loads((ROOT/'contracts/problem/codes.json').read_text())
  self.assertFalse(schema['additionalProperties'])
  self.assertEqual(set(codes),set(schema['properties']['code']['enum']))
  source=(ROOT/'shared/common-problem/src/main/java/com/nigson/shared/problem/ProblemCode.java').read_text()
  java={code:{'status':int(status),'title':title,'detail':detail} for code,status,title,detail in re.findall(r'([A-Z_]+)\((\d+),"([^"]*)","([^"]*)"\)',source)}
  self.assertEqual(codes,java)
 def test_route_catalog_includes_all_declared_apis_and_is_identical(self):
  routes=json.loads((ROOT/'contracts/problem/routes.json').read_text())
  master=(ROOT/'BACKEND_IMPLEMENTATION_MASTER.md').read_text()
  declared=set(re.findall(r'^### API-\d+ (?:GET|POST|PUT|PATCH|DELETE) (\S+)',master,re.M))
  self.assertTrue(declared<=set(routes))
  for name in ('shared/common-observability/src/main/resources/observability/routes.json','services/ai/src/nigson_ai/platform/routes.json'):
   self.assertEqual(routes,json.loads((ROOT/name).read_text()))
 def test_audit_states_remain_closed_and_match_runtime_owners(self):
  states=json.loads((ROOT/'contracts/problem/audit-states.json').read_text())
  self.assertEqual(len(states),len(set(states)))
  for name in ('shared/common-problem/src/main/resources/audit/states.json','services/ai/src/nigson_ai/platform/audit_states.json'):
   self.assertEqual(states,json.loads((ROOT/name).read_text()))
