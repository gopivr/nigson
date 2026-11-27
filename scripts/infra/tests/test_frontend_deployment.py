import copy
import hashlib
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
spec = importlib.util.spec_from_file_location('frontend_deploy', ROOT / 'scripts/infra/deploy-first-account-frontend.py')
deploy = importlib.util.module_from_spec(spec); spec.loader.exec_module(deploy)
from test_first_account_pipeline import FakeCommands, ACCOUNT, applied_variables

COMMIT = 'a' * 40
RELEASE = COMMIT + '-123-1'
DIST = 'E123ABC'


def plan():
    before = {'id': DIST, 'arn': f'arn:aws:cloudfront::{ACCOUNT}:distribution/{DIST}', 'web_acl_id': 'unchanged-waf', 'etag': 'old', 'origin': [
        {'origin_id': 'static', 'domain_name': 'frontend.s3.amazonaws.com', 'origin_path': '/releases/initial', 'origin_access_control_id': 'signed'},
        {'origin_id': 'backend', 'domain_name': 'api.stage.nigson.com', 'origin_path': ''}]}
    after = copy.deepcopy(before); after['origin'][0]['origin_path'] = '/releases/' + RELEASE; after.pop('etag')
    return {'resource_changes': [{'address': deploy.ADDRESS, 'type': 'aws_cloudfront_distribution', 'change': {'actions': ['update'], 'before': before, 'after': after, 'after_unknown': {'etag': True}}}]}


def artifact(folder):
    site = folder / 'site'; (site / 'remotes/shop').mkdir(parents=True)
    for name, content in {'index.html': '<html>Synthetic isolated test</html>', 'remoteEntry.json': '{}', 'federation.manifest.json': '{"shop":"/remotes/shop/remoteEntry.json"}', 'remotes/shop/remoteEntry.json': '{}'}.items():
        (site / name).write_text(content)
    proof = folder / 'build.json'
    proof.write_text(json.dumps({'schema_version': 1, 'source_commit': COMMIT, 'files': {p.relative_to(site).as_posix(): hashlib.sha256(p.read_bytes()).hexdigest() for p in site.rglob('*') if p.is_file()}}))
    return site, proof


class FrontendCommands(FakeCommands):
    def __init__(self, proposed=None, snapshot=True):
        super().__init__()
        self.proposed = proposed or plan()
        variables = applied_variables(activate_applications=True, activate_identity=True, publish_task_definitions=True, image_digests={'commerce':'sha256:'+'b'*64}, application_environment={'commerce':{'APP_ENV':'stage','NIGSON_SECURITY_ENABLED':'true'}})
        resources = [{'address': 'module.frontend.aws_s3_bucket.frontend', 'values': {'bucket': f'nigson-stage-{ACCOUNT}-frontend'}}, {'address': deploy.ADDRESS, 'values': {'id': DIST}}]
        self.full_state = {'values': {'outputs': {'deployment_configuration': {'value': {'schema_version': 1, 'variables': variables}}} if snapshot else {}, 'root_module': {'resources': resources}}}

    def call(self, args, capture=False):
        if 'show' in args:
            self.calls.append(args)
            return json.dumps(self.full_state if args[-1] == '-json' else self.proposed)
        return super().call(args, capture)

    def aws(self, *args, **kwargs):
        if args[:2] == ('cloudfront', 'get-distribution'):
            self.calls.append(list(args))
            return {'Distribution': {'ARN': f'arn:aws:cloudfront::{ACCOUNT}:distribution/{DIST}', 'DistributionConfig': {'Aliases': {'Items': ['stage.nigson.com']}}}}
        if args[:2] == ('cloudfront', 'create-invalidation'):
            self.calls.append(list(args)); return {'Invalidation': {'Id': 'I123'}}
        return super().aws(*args, **kwargs)


class FrontendDeploymentTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(); self.addCleanup(self.temp.cleanup)
        self.folder = Path(self.temp.name); self.site, self.proof = artifact(self.folder)
        patcher = patch.dict(os.environ, {'AWS_EXPECTED_ACCOUNT_ID': ACCOUNT, 'AWS_REGION': 'eu-west-1'})
        patcher.start(); self.addCleanup(patcher.stop)

    def execute(self, fake):
        with patch.object(deploy.infra.Pipeline, 'summary'):
            deploy.deploy(self.site, self.proof, RELEASE, COMMIT, commands=fake, work=self.folder/'work', probe=lambda site: None)

    def test_artifact_tampering_is_rejected(self):
        (self.site/'index.html').write_text('changed')
        with self.assertRaisesRegex(ValueError, 'sealed'):
            deploy.validate_artifact(self.site, self.proof, COMMIT)

    def test_artifact_from_another_commit_is_rejected(self):
        with self.assertRaises(ValueError):
            deploy.validate_artifact(self.site, self.proof, 'b'*40)

    def test_plan_allows_only_static_origin_release(self):
        deploy.frontend_plan(plan(), RELEASE, DIST)

    def test_zero_and_unset_provider_timeout_are_equivalent(self):
        p=plan(); p['resource_changes'][0]['change']['before']['origin'][0]['response_completion_timeout']=0
        p['resource_changes'][0]['change']['after']['origin'][0]['response_completion_timeout']=None
        deploy.frontend_plan(p, RELEASE, DIST)

    def test_live_provider_empty_origin_fields_and_unknown_timeout_are_equivalent(self):
        p = plan()
        change = p['resource_changes'][0]['change']
        for side in ('before', 'after'):
            for origin in change[side]['origin']:
                origin.update(connection_attempts=3, connection_timeout=10)
        static, backend = change['before']['origin']
        static.update(response_completion_timeout=0, custom_header=[],
                      custom_origin_config=[], origin_shield=[], s3_origin_config=[], vpc_origin_config=[])
        backend.update(response_completion_timeout=0, origin_access_control_id='',
                       custom_origin_config=[{'http_port': 80, 'https_port': 443,
                       'origin_protocol_policy': 'https-only', 'origin_read_timeout': 30,
                       'origin_keepalive_timeout': 5, 'origin_ssl_protocols': ['TLSv1.2'],
                       'ip_address_type': ''}])
        new_static, new_backend = change['after']['origin']
        new_static.update(response_completion_timeout=None, custom_header=None,
                          custom_origin_config=None, origin_shield=None,
                          s3_origin_config=None, vpc_origin_config=None)
        new_backend.update(response_completion_timeout=0, origin_access_control_id=None,
                           origin_path=None, custom_origin_config=copy.deepcopy(backend['custom_origin_config']))
        new_backend['custom_origin_config'][0]['ip_address_type'] = None
        change['after_unknown']['origin'] = [
            {'response_completion_timeout': True}, {}]
        # Terraform set elements may be serialized in a different order.
        change['after']['origin'].reverse()
        original = copy.deepcopy(p)
        deploy.frontend_plan(p, RELEASE, DIST)
        self.assertEqual(p, original)

    def test_nonempty_origin_settings_cannot_be_removed_or_changed(self):
        for key, value in [('origin_access_control_id', 'configured-oac'),
                           ('origin_path', '/backend-base'),
                           ('custom_header', [{'name': 'Authorization', 'value': 'private-test-header'}]),
                           ('origin_shield', [{'enabled': True, 'origin_shield_region': 'us-east-2'}]),
                           ('s3_origin_config', [{'origin_access_identity': 'configured-identity'}]),
                           ('vpc_origin_config', [{'vpc_origin_id': 'configured-vpc'}])]:
            p = plan()
            change = p['resource_changes'][0]['change']
            change['before']['origin'][1][key] = value
            change['after']['origin'][1][key] = None
            with self.subTest(key=key), self.assertRaises(ValueError):
                deploy.frontend_plan(p, RELEASE, DIST)

    def test_configured_backend_ip_address_type_remains_protected(self):
        p = plan()
        change = p['resource_changes'][0]['change']
        change['before']['origin'][1]['custom_origin_config'] = [{'ip_address_type': 'ipv6'}]
        change['after']['origin'][1]['custom_origin_config'] = [{'ip_address_type': None}]
        with self.assertRaises(ValueError):
            deploy.frontend_plan(p, RELEASE, DIST)

    def test_configured_timeout_changes_remain_rejected(self):
        p=plan(); p['resource_changes'][0]['change']['before']['origin'][0]['response_completion_timeout']=30
        p['resource_changes'][0]['change']['after']['origin'][0]['response_completion_timeout']=None
        with self.assertRaises(ValueError): deploy.frontend_plan(p, RELEASE, DIST)

    def test_plan_rejects_backend_origin_or_waf_changes(self):
        for target in ['origin', 'web_acl_id']:
            p = plan()
            if target == 'origin': p['resource_changes'][0]['change']['after']['origin'][1]['domain_name'] = 'untrusted.example'
            else: p['resource_changes'][0]['change']['after'][target] = 'different'
            with self.subTest(target=target), self.assertRaises(ValueError):
                deploy.frontend_plan(p, RELEASE, DIST)

    def test_rejection_identifies_changed_fields_without_exposing_values(self):
        p = plan()
        after = p['resource_changes'][0]['change']['after']
        after['web_acl_id'] = 'private-waf-value'
        after['tags'] = {'private-tag-name': 'private-tag-value'}
        after['origin'][1]['custom_header'] = [{'name': 'private-header', 'value': 'private-header-value'}]
        with self.assertRaises(ValueError) as failure:
            deploy.frontend_plan(p, RELEASE, DIST)
        message = str(failure.exception)
        self.assertIn('/web_acl_id', message)
        self.assertIn('/tags', message)
        self.assertIn('custom_header', message)
        self.assertNotIn('private-', message)

    def test_nested_certificate_drift_is_reported_and_rejected(self):
        p = plan()
        change = p['resource_changes'][0]['change']
        change['before']['viewer_certificate'] = [{'minimum_protocol_version': 'TLSv1.2_2021'}]
        change['after']['viewer_certificate'] = [{'minimum_protocol_version': 'TLSv1'}]
        with self.assertRaisesRegex(ValueError, '/viewer_certificate/0/minimum_protocol_version'):
            deploy.frontend_plan(p, RELEASE, DIST)

    def test_diagnostic_field_count_is_bounded_and_tag_keys_are_private(self):
        before = {f'field_{i}': 'old' for i in range(100)}
        after = {f'field_{i}': 'new' for i in range(100)}
        self.assertEqual(len(deploy.changed_field_paths(before, after)), 20)
        self.assertEqual(deploy.changed_field_paths({'tags': {'private': 'old'}}, {'tags': {'private': 'new'}}), ['/tags'])

    def test_plan_rejects_duplicate_origin_identifiers(self):
        p=plan(); p['resource_changes'][0]['change']['after']['origin'].append(copy.deepcopy(p['resource_changes'][0]['change']['after']['origin'][0]))
        with self.assertRaises(ValueError):deploy.frontend_plan(p, RELEASE, DIST)

    def test_plan_rejects_runtime_or_database_changes(self):
        p = plan(); p['resource_changes'].append({'address': 'module.database.instance', 'change': {'actions': ['update']}})
        with self.assertRaises(ValueError): deploy.frontend_plan(p, RELEASE, DIST)

    def test_plan_rejects_replacement(self):
        p=plan(); p['resource_changes'][0]['change']['actions']=['delete','create']
        with self.assertRaises(ValueError): deploy.frontend_plan(p, RELEASE, DIST)

    def test_deploy_preserves_runtime_inputs_and_uploads_before_activation(self):
        fake=FrontendCommands(); self.execute(fake)
        values=json.loads((self.folder/'work/release.tfvars.json').read_text())
        self.assertTrue(values['activate_applications']); self.assertTrue(values['activate_identity'])
        self.assertEqual(values['application_environment']['commerce']['APP_ENV'], 'stage')
        self.assertEqual(values['frontend_release_id'], RELEASE)
        self.assertEqual((self.folder/'work/release.tfvars.json').stat().st_mode & 0o777, 0o600)
        uploaded=next(i for i,c in enumerate(fake.calls) if c[0]=='python3')
        applied=next(i for i,c in enumerate(fake.calls) if 'apply' in c)
        self.assertLess(uploaded,applied)
        self.assertTrue(any('invalidation-completed' in c for c in fake.calls))

    def test_infra_drift_fails_before_upload(self):
        p=plan(); p['resource_changes'].append({'address':'other','change':{'actions':['create']}})
        fake=FrontendCommands(p)
        with self.assertRaises(ValueError): self.execute(fake)
        self.assertFalse(any(c[0]=='python3' or 'apply' in c for c in fake.calls))

    def test_missing_snapshot_requires_updated_infra_apply(self):
        fake=FrontendCommands(snapshot=False)
        with self.assertRaisesRegex(ValueError,'Apply the updated'): self.execute(fake)
        self.assertFalse(any(c[0]=='python3' or 'apply' in c for c in fake.calls))

    def test_incomplete_snapshot_cannot_reset_runtime_defaults(self):
        fake=FrontendCommands(); del fake.full_state['values']['outputs']['deployment_configuration']['value']['variables']['image_digests']
        with self.assertRaisesRegex(ValueError,'incomplete'):self.execute(fake)
        self.assertFalse(any(c[0]=='python3' or 'apply' in c for c in fake.calls))

    def test_probe_accepts_exact_shell_manifest_and_remote_content(self):
        site=self.site
        class Response:
            status=200
            def __init__(self,url):self.url=url
            def __enter__(self):return self
            def __exit__(self,*args):pass
            def read(self,limit):return (site/self.url.removeprefix('https://stage.nigson.com/')).read_bytes()
            def geturl(self):return self.url
        class Opener:
            def open(self,request,**kwargs):return Response(request.full_url)
        deploy.smoke(site,Opener())

    def test_changed_destination_is_rejected(self):
        fake=FrontendCommands(); fake.full_state['values']['root_module']['resources'][0]['values']['bucket']='foreign-bucket'
        with self.assertRaises(ValueError): self.execute(fake)
        self.assertFalse(any(c[0]=='python3' for c in fake.calls))

    def test_current_configuration_is_bound_to_account(self):
        fake=FrontendCommands();fake.full_state['values']['outputs']['deployment_configuration']['value']['variables']['expected_account_id']='999999999999'
        with self.assertRaises(ValueError): self.execute(fake)

    def test_probe_rejects_stale_or_redirected_content(self):
        class Response:
            status=200
            def __enter__(self): return self
            def __exit__(self,*args): pass
            def read(self,limit): return b'old content'
            def geturl(self): return 'https://stage.nigson.com/index.html'
        class Opener:
            def open(self,*args,**kwargs): return Response()
        with self.assertRaises(ValueError): deploy.smoke(self.site,Opener())
