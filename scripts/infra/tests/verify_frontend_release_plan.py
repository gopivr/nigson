"""Exercise the deployment guard against a real mocked AWS provider CloudFront plan."""
import importlib.util
import json
import re
from pathlib import Path
import sys

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('frontend', ROOT / 'scripts/infra/deploy-first-account-frontend.py')
frontend = importlib.util.module_from_spec(spec); spec.loader.exec_module(frontend)
summary = None
plan = None
configuration = None
with Path(sys.argv[1]).open() as source:
    for line in source:
        event = json.loads(line)
        if event.get('type') == 'test_summary': summary = event['test_summary']
        if event.get('@testrun') == 'record_applied_runtime_configuration_for_ui_releases' and 'test_plan' in event:
            configuration = event['test_plan']['output_changes']['deployment_configuration']['after']['variables']
        if event.get('@testrun') == 'frontend_release_plan_updates_static_origin_only' and 'test_plan' in event:
            plan = {'resource_changes': event['test_plan']['resource_changes']}
if not summary or summary.get('status') != 'pass' or not plan:
    raise SystemExit('FAIL: passing frontend release-plan test is required')
resource = next(c for c in plan['resource_changes'] if c['address'] == frontend.ADDRESS)
frontend.frontend_plan(plan, 'a'*40+'-123-1', resource['change']['before']['id'])
print('PASS: AWS6.15 CloudFront release-only plan passes the production activation guard')

fields = set(re.findall(r'^variable\s+"([^\"]+)"', (ROOT / 'infrastructure/terraform/aws/first-account/variables.tf').read_text(), re.MULTILINE))
if configuration is None or set(configuration) != fields:
    raise SystemExit('FAIL: applied snapshot must retain every infrastructure/runtime input')
print('PASS: applied configuration snapshot retains every root input')
