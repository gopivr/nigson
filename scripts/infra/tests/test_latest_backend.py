"""Offline latest-artifact deployment tests; no AWS or GitHub requests."""
import copy
import importlib.util
import json
import os
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

ROOT = Path(__file__).resolve().parents[3]
def load(name, file):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts/infra' / file)
    module = importlib.util.module_from_spec(spec); spec.loader.exec_module(module)
    return module
D = load('latest_backend', 'deploy-latest-backend.py')
S = load('select_backend', 'select-backend-release.py')
ACCOUNT, REGION, COMMIT = '123456789012', 'us-east-2', 'a' * 40
DIGEST, SCAN = 'sha256:' + 'b' * 64, 'sha256:' + 'c' * 64


def release(environment='stage', phase=1):
    names = ['commerce', 'experience', 'integration', 'identity', 'migration'] + (['ai'] if phase == 5 else [])
    return {'schema_version': 1, 'environment': environment, 'account_id': ACCOUNT, 'region': REGION,
            'architecture': 'arm64', 'product_phase': phase, 'source_commit': COMMIT,
            'source_run_id': '123', 'source_run_attempt': '1', 'components': {
                name: {'image': f'{ACCOUNT}.dkr.ecr.{REGION}.amazonaws.com/nigson/{environment}/' + ('commerce' if name == 'migration' else name) + '@' + DIGEST,
                       'scan_digest': SCAN, 'critical': 0, 'high': 0} for name in names}}


class FakeAWS:
    def __init__(self, environment='stage', phase=1):
        self.env, self.phase, self.calls, self.registered = environment, phase, [], {}
        self.cluster = f'arn:aws:ecs:{REGION}:{ACCOUNT}:cluster/nigson-{environment}'
        self.old = {s: f'arn:aws:ecs:{REGION}:{ACCOUNT}:task-definition/nigson-{environment}-{s}:1' for s in ['identity', 'commerce', 'experience', 'integration', 'ai']}
        self.current = dict(self.old)
        self.wait_failure = None
        self.high = 0
        self.inactive = False
        self.fail_update = None
        self.fail_receipt = False
        self.payloads = []

    def service(self, name):
        s = name.removeprefix('nigson-' + self.env + '-')
        return {'serviceName': name, 'clusterArn': self.cluster, 'status': 'ACTIVE',
                'desiredCount': 0 if self.inactive else 2, 'runningCount': 2, 'pendingCount': 0,
                'deployments': [{'rolloutState': 'COMPLETED'}], 'taskDefinition': self.current[s],
                'networkConfiguration': {'awsvpcConfiguration': {'subnets': ['subnet-12345678', 'subnet-87654321'], 'assignPublicIp': 'DISABLED'}}}

    def call(self, aws_service, operation, **kw):
        service = aws_service
        self.calls.append((service, operation, copy.deepcopy(kw)))
        if (service, operation) == ('sts', 'get-caller-identity'): return {'Account': ACCOUNT}
        if operation == 'describe-images': return {'imageDetails': [{'imageDigest': kw['image_ids'][0]['imageDigest']}]}
        if operation == 'describe-image-scan-findings': return {'imageId': {'imageDigest': SCAN}, 'imageScanStatus': {'status': 'COMPLETE'}, 'imageScanFindings': {'findingSeverityCounts': {'HIGH': self.high}}}
        if operation == 'describe-services': return {'services': [self.service(s) for s in kw['services']], 'failures': []}
        if operation == 'describe-task-definition':
            family = kw['task_definition'].split('/')[-1].split(':')[0]
            migration = '-migration-BP' in family
            owner = family.removeprefix('nigson-' + self.env + '-').split('-migration-BP')[0]
            suffix = '-migration' if migration else ''
            env = [{'name': 'APP_ENV', 'value': self.env}]
            if migration: env += [{'name': 'MIGRATION_OWNER', 'value': owner}, {'name': 'MIGRATION_RELEASE', 'value': 'BP' + str(self.phase)}]
            td = {'family': family, 'taskDefinitionArn': kw['task_definition'] if kw['task_definition'].startswith('arn:') else f'arn:aws:ecs:{REGION}:{ACCOUNT}:task-definition/{family}:1',
                  'networkMode': 'awsvpc', 'requiresCompatibilities': ['FARGATE'], 'cpu': '1024', 'memory': '2048',
                  'executionRoleArn': f'arn:aws:iam::{ACCOUNT}:role/nigson-{self.env}-{owner}{suffix}-execution',
                  'taskRoleArn': f'arn:aws:iam::{ACCOUNT}:role/nigson-{self.env}-{owner}{suffix}-task',
                  'runtimePlatform': {'operatingSystemFamily': 'LINUX', 'cpuArchitecture': 'X86_64'},
                  'containerDefinitions': [{'name': 'owner-migration' if migration else ('keycloak' if owner == 'identity' else owner),
                                           'image': 'old-image', 'environment': env,
                                           'secrets': [{'name': 'PASSWORD', 'valueFrom': 'arn:aws:secretsmanager:unchanged'}],
                                           'healthCheck': {'command': ['CMD', 'probe']}, 'readonlyRootFilesystem': True}]}
            return {'taskDefinition': td, 'tags': []}
        if operation == 'describe-subnets': return {'Subnets': [{'SubnetId': s, 'VpcId': 'vpc-12345678'} for s in kw['subnet_ids']]}
        if operation == 'describe-security-groups': return {'SecurityGroups': [{'GroupId': 'sg-12345678', 'VpcId': 'vpc-12345678'}]}
        if operation == 'register-task-definition':
            body = json.loads(Path(kw['cli_input_json'].removeprefix('file://')).read_text())
            self.payloads.append(body)
            arn = f'arn:aws:ecs:{REGION}:{ACCOUNT}:task-definition/{body["family"]}:2'
            self.registered[body['family']] = arn
            return {'taskDefinition': {'taskDefinitionArn': arn}}
        if operation == 'run-task': return {'tasks': [{'taskArn': f'arn:aws:ecs:{REGION}:{ACCOUNT}:task/nigson-{self.env}/abcdef1234'}], 'failures': []}
        if operation == 'describe-tasks': return {'tasks': [{'lastStatus': 'STOPPED', 'containers': [{'name': 'owner-migration', 'exitCode': 1 if self.fail_receipt else 0}]}]}
        if operation == 'update-service':
            owner = kw['service'].removeprefix('nigson-' + self.env + '-')
            if self.fail_update == owner and kw['task_definition'].endswith(':2'):
                self.current[owner] = kw['task_definition']
                raise RuntimeError('Uncertain update response')
            self.current[owner] = kw['task_definition']
            return {}
        raise AssertionError((service, operation))

    def wait(self, waiter, **kw):
        self.calls.append(('wait', waiter, copy.deepcopy(kw)))
        if self.wait_failure == waiter: raise RuntimeError('Unconfirmed waiter')


class LatestBackendTests(unittest.TestCase):
    def setUp(self):
        self.tmp = tempfile.TemporaryDirectory(); self.addCleanup(self.tmp.cleanup)
        self.work = Path(self.tmp.name) / 'deploy'

    def run_release(self, aws=None, value=None):
        value = value or release(); aws = aws or FakeAWS()
        checked = D.validate_release(value, value['environment'], ACCOUNT, REGION, COMMIT, '123', '1')
        deploy = D.Deployer(checked, aws, self.work)
        result = deploy.run()
        return deploy, aws, result

    def test_complete_cohort_migrates_before_rollout_and_aligns_architecture(self):
        dep, aws, result = self.run_release()
        self.assertEqual(result['status'], 'SUCCEEDED')
        self.assertEqual(len([c for c in aws.calls if c[1] == 'run-task']), 3)
        last_migration = max(i for i,c in enumerate(aws.calls) if c[1] == 'describe-tasks')
        first_rollout = next(i for i,c in enumerate(aws.calls) if c[1] == 'update-service')
        self.assertLess(last_migration, first_rollout)
        for body in aws.payloads:
            self.assertEqual(body['runtimePlatform']['cpuArchitecture'], 'ARM64')
            self.assertEqual(body['containerDefinitions'][0]['secrets'][0]['valueFrom'], 'arn:aws:secretsmanager:unchanged')
            self.assertTrue(body['containerDefinitions'][0]['readonlyRootFilesystem'])
        saved=(self.work/'deployment.json').read_text()
        self.assertNotIn('PASSWORD', saved)
        self.assertEqual((self.work/'deployment.json').stat().st_mode & 0o777, 0o600)
        self.assertEqual(aws.calls[first_rollout][2]['service'], 'nigson-stage-identity')

    def test_latest_approved_template_config_is_used_and_running_revision_is_retained(self):
        aws=FakeAWS();original=aws.call
        def call(aws_service, operation, **kw):
            result=original(aws_service, operation, **kw)
            if operation=='describe-task-definition' and kw['task_definition']=='nigson-stage-commerce':
                result['taskDefinition']['taskDefinitionArn']=result['taskDefinition']['taskDefinitionArn'].rsplit(':',1)[0]+':7'
                result['taskDefinition']['containerDefinitions'][0]['environment'].append({'name':'CONFIG_REVISION','value':'approved-new-template'})
            return result
        aws.call=call
        dep,aws,result=self.run_release(aws)
        payload=next(p for p in aws.payloads if p['family']=='nigson-stage-commerce')
        self.assertIn({'name':'CONFIG_REVISION','value':'approved-new-template'},payload['containerDefinitions'][0]['environment'])
        self.assertEqual(result['previous_task_definitions']['commerce'],aws.old['commerce'])

    def test_wrong_environment_and_incomplete_or_unqualified_receipts_are_refused(self):
        for update in [{'environment':'prod'}, {'account_id':'999999999999'}, {'source_commit':'b'*40}, {'source_run_id':'999'}]:
            r=release();r.update(update)
            with self.subTest(update=update), self.assertRaises(ValueError): D.validate_release(r,'stage',ACCOUNT,REGION,COMMIT,'123','1')
        r=release();del r['components']['migration']
        with self.assertRaises(ValueError):D.validate_release(r,'stage',ACCOUNT,REGION,COMMIT,'123','1')
        r=release();r['components']['commerce']['high']=1
        with self.assertRaises(ValueError):D.validate_release(r,'stage',ACCOUNT,REGION,COMMIT,'123','1')

    def test_fresh_vulnerability_findings_block_all_mutations(self):
        aws=FakeAWS();aws.high=1
        with self.assertRaisesRegex(ValueError,'scan blocks'):self.run_release(aws)
        self.assertFalse(any(c[1] in {'register-task-definition','run-task','update-service'} for c in aws.calls))

    def test_missing_runtime_activation_fails_before_registration(self):
        aws=FakeAWS();aws.inactive=True
        with self.assertRaisesRegex(ValueError,'activation'):self.run_release(aws)
        self.assertFalse(any(c[1]=='register-task-definition' for c in aws.calls))

    def test_foundation_only_installation_reports_missing_services_and_saves_safe_receipt(self):
        aws=FakeAWS();original=aws.call
        def call(aws_service, operation, **kw):
            if operation=='describe-services':
                return {'services': [], 'failures': [{'arn': 'do-not-copy-provider-response', 'reason': 'MISSING'}]}
            return original(aws_service, operation, **kw)
        aws.call=call
        with self.assertRaisesRegex(ValueError,'Missing services: nigson-stage-identity') as error:
            self.run_release(aws)
        self.assertIn('rerunning its apply does not create runtime services',str(error.exception))
        saved=(self.work/'deployment.json').read_text()
        record=json.loads(saved)
        self.assertEqual(record['status'],'PREFLIGHT_BLOCKED')
        self.assertEqual(record['service_readiness'],dict.fromkeys(['identity','commerce','experience','integration'],'MISSING'))
        self.assertNotIn('do-not-copy-provider-response',saved)
        self.assertFalse(any(c[1] in {'register-task-definition','run-task','update-service'} for c in aws.calls))

    def test_scaled_to_zero_preflight_keeps_diagnostic_artifact(self):
        aws=FakeAWS();aws.inactive=True
        with self.assertRaisesRegex(ValueError,'activation'):self.run_release(aws)
        record=json.loads((self.work/'deployment.json').read_text())
        self.assertEqual(record['status'],'PREFLIGHT_BLOCKED')
        self.assertTrue(all(value=='SCALED_TO_ZERO' for value in record['service_readiness'].values()))

    def test_wrong_account_stops_before_any_other_aws_access(self):
        aws=FakeAWS();original=aws.call
        aws.call=lambda service,operation,**kw: {'Account':'999999999999'} if service=='sts' else original(service,operation,**kw)
        with self.assertRaisesRegex(ValueError,'identity'):self.run_release(aws)
        self.assertEqual(aws.calls,[])

    def test_migration_wait_timeout_preserves_unknown_without_retry_or_rollout(self):
        aws=FakeAWS();aws.wait_failure='tasks-stopped'
        with self.assertRaises(RuntimeError):self.run_release(aws)
        self.assertEqual(len([c for c in aws.calls if c[1]=='run-task']),1)
        self.assertFalse(any(c[1]=='update-service' for c in aws.calls))
        record=json.loads((self.work/'deployment.json').read_text())
        self.assertEqual(record['migration_tasks']['commerce']['status'],'UNKNOWN')

    def test_failed_migration_never_rolls_out_services(self):
        aws=FakeAWS();aws.fail_receipt=True
        with self.assertRaises(RuntimeError):self.run_release(aws)
        self.assertFalse(any(c[1]=='update-service' for c in aws.calls))

    def test_uncertain_service_update_rolls_back_attempted_app_services(self):
        aws=FakeAWS();aws.fail_update='experience'
        with self.assertRaises(RuntimeError):self.run_release(aws)
        self.assertEqual(aws.current['commerce'],aws.old['commerce'])
        self.assertEqual(aws.current['experience'],aws.old['experience'])
        record=json.loads((self.work/'deployment.json').read_text())
        self.assertEqual(record['rollback']['commerce'],'SUCCEEDED')
        self.assertIn('MANUAL_SCHEMA',record['rollback']['identity'])

    def test_identity_failure_does_not_blindly_reverse_identity_database_upgrade(self):
        aws=FakeAWS();aws.fail_update='identity'
        with self.assertRaises(RuntimeError):self.run_release(aws)
        self.assertEqual(len([c for c in aws.calls if c[1]=='update-service']),1)
        self.assertIn('MANUAL_SCHEMA',json.loads((self.work/'deployment.json').read_text())['rollback']['identity'])

    def test_circuit_breaker_returning_to_old_revision_is_a_failed_release(self):
        aws=FakeAWS();original=aws.wait
        def wait(waiter, **kw):
            original(waiter, **kw)
            if waiter=='services-stable' and kw['services']==['nigson-stage-commerce']:
                aws.current['commerce']=aws.old['commerce']
        aws.wait=wait
        with self.assertRaisesRegex(RuntimeError,'candidate revision'):self.run_release(aws)
        record=json.loads((self.work/'deployment.json').read_text())
        self.assertEqual(record['status'],'FAILED')
        self.assertEqual(record['rollback']['commerce'],'SUCCEEDED')

    def test_cross_environment_task_roles_fail_before_any_mutation(self):
        aws=FakeAWS();original=aws.call
        def call(aws_service, operation, **kw):
            result=original(aws_service, operation, **kw)
            if operation=='describe-task-definition':
                result['taskDefinition']['taskRoleArn']=result['taskDefinition']['taskRoleArn'].replace('-stage-','-prod-')
            return result
        aws.call=call
        with self.assertRaisesRegex(ValueError,'task role'):self.run_release(aws)
        self.assertFalse(any(c[1] in {'register-task-definition','run-task','update-service'} for c in aws.calls))

    def test_repeated_release_reuses_migration_revision_and_client_token(self):
        tokens=[]
        for index in range(2):
            aws=FakeAWS();original=aws.call
            def call(aws_service, operation, **kw):
                result=original(aws_service, operation, **kw)
                if operation=='describe-task-definition' and '-migration-BP' in kw['task_definition']:
                    result['taskDefinition']['containerDefinitions'][0]['image']=release()['components']['migration']['image']
                    result['taskDefinition']['runtimePlatform']['cpuArchitecture']='ARM64'
                return result
            aws.call=call
            self.work=Path(self.tmp.name)/str(index)
            self.run_release(aws)
            self.assertFalse(any('-migration-' in p['family'] for p in aws.payloads))
            tokens.append([c[2]['client_token'] for c in aws.calls if c[1]=='run-task'])
        self.assertEqual(tokens[0],tokens[1])

    def test_production_and_ai_use_the_selected_environment(self):
        dep,aws,result=self.run_release(FakeAWS('prod',5),release('prod',5))
        self.assertEqual(len(result['migration_tasks']),4)
        self.assertEqual(len([c for c in aws.calls if c[1]=='update-service']),5)
        self.assertTrue(all('/nigson/prod/' in c['image'] for b in aws.payloads for c in b['containerDefinitions']))

    def test_false_scan_counts_are_not_a_pass(self):
        r=release();r['components']['commerce']['critical']=False
        with self.assertRaises(ValueError):D.validate_release(r,'stage',ACCOUNT,REGION,COMMIT,'123','1')

    def test_aws_cli_boolean_switches_are_not_serialized_as_strings(self):
        with patch.dict(os.environ,{'AWS_REGION':REGION}),patch.object(D.subprocess,'run') as run:
            run.return_value.returncode=0;run.return_value.stdout='{}'
            D.AWS().call('ecs','update-service',force_new_deployment=True)
            args=run.call_args.args[0]
            self.assertIn('--force-new-deployment',args);self.assertNotIn('True',args)


class SelectionTests(unittest.TestCase):
    def test_latest_successful_environment_cohort_uses_exact_attempt_artifact(self):
        calls=[]
        def get(path):
            calls.append(path)
            if '/artifacts?' not in path:
                return {'workflow_runs':[{'id':124,'head_sha':'b'*40,'run_attempt':1,'status':'completed','conclusion':'success','head_branch':'main','event':'workflow_dispatch'},
                                         {'id':123,'head_sha':COMMIT,'run_attempt':2,'status':'completed','conclusion':'success','head_branch':'main','event':'workflow_dispatch'}]}
            if '/124/' in path:return {'artifacts':[{'id':10,'name':'backend-release-prod-'+'b'*40+'-1','expired':False}]}
            return {'artifacts':[{'id':11,'name':f'backend-release-stage-{COMMIT}-1','expired':False},
                                  {'id':12,'name':f'backend-release-stage-{COMMIT}-2','expired':False}]}
        result=S.select_release(get,'stage','main')
        self.assertEqual(result,{'artifact_id':'12','run_id':'123','source_commit':COMMIT,'source_run_attempt':'2'})

    def test_no_successful_complete_release_fails_without_a_latest_tag_fallback(self):
        with self.assertRaisesRegex(ValueError,'No complete'):S.select_release(lambda path:{'workflow_runs':[]},'stage','main')

    def test_expired_newest_matching_artifact_is_not_replaced_by_an_older_release(self):
        def get(path):
            if '/artifacts?' not in path:return {'workflow_runs':[{'id':123,'head_sha':COMMIT,'status':'completed','conclusion':'success','head_branch':'main','event':'workflow_dispatch'}]}
            return {'artifacts':[{'id':12,'name':f'backend-release-stage-{COMMIT}-1','expired':True}]}
        with self.assertRaisesRegex(ValueError,'expired'):S.select_release(get,'stage','main')


class OperationsReleaseTests(unittest.TestCase):
    def test_v2_receipt_requires_a_qualified_identity_operations_image(self):
        value=release();value['schema_version']=2
        with self.assertRaisesRegex(ValueError,'operations image'):
            D.validate_release(value,'stage',ACCOUNT,REGION,COMMIT,'123','1')
        value['components']['operations']=dict(value['components']['identity'])
        self.assertEqual(D.validate_release(value,'stage',ACCOUNT,REGION,COMMIT,'123','1'),value)
        value['components']['operations']['high']=1
        with self.assertRaisesRegex(ValueError,'Unqualified'):
            D.validate_release(value,'stage',ACCOUNT,REGION,COMMIT,'123','1')

    @staticmethod
    def publication(run_id, commit=COMMIT):
        return {'id':run_id,'head_sha':commit,'run_attempt':1,'status':'completed','conclusion':'success','head_branch':'main','event':'workflow_dispatch'}

    def test_runtime_selection_skips_legacy_cohort_and_pins_latest_compatible_artifact(self):
        def get(path):
            if '/artifacts?' not in path:
                return {'workflow_runs':[self.publication(124,'b'*40),self.publication(123)]}
            if '/124/' in path:return {'artifacts':[{'id':21,'name':f'backend-release-stage-{"b"*40}-1','expired':False}]}
            return {'artifacts':[{'id':22,'name':f'backend-release-v2-stage-{COMMIT}-1','expired':False}]}
        self.assertEqual(S.select_release(get,'stage','main',require_operations=True)['artifact_id'],'22')
        self.assertEqual(S.select_release(get,'stage','main')['artifact_id'],'21')

    def test_legacy_only_publication_explains_new_successful_build_is_required(self):
        def get(path):
            if '/artifacts?' not in path:return {'workflow_runs':[self.publication(123)]}
            return {'artifacts':[{'id':22,'name':f'backend-release-stage-{COMMIT}-1','expired':False}]}
        with self.assertRaisesRegex(ValueError,'Run Backend — Build and Publish Images on the current default branch'):
            S.select_release(get,'stage','main',require_operations=True)

    def test_expired_v2_artifact_is_not_replaced_by_a_legacy_cohort(self):
        def get(path):
            if '/artifacts?' not in path:return {'workflow_runs':[self.publication(123)]}
            return {'artifacts':[{'id':22,'name':f'backend-release-v2-stage-{COMMIT}-1','expired':True},
                                  {'id':21,'name':f'backend-release-stage-{COMMIT}-1','expired':False}]}
        with self.assertRaisesRegex(ValueError,'expired'):
            S.select_release(get,'stage','main',require_operations=True)

    def test_v2_is_preferred_when_both_artifact_versions_exist_on_one_run(self):
        def get(path):
            if '/artifacts?' not in path:return {'workflow_runs':[self.publication(123)]}
            return {'artifacts':[{'id':22,'name':f'backend-release-v2-stage-{COMMIT}-1','expired':False},
                                  {'id':21,'name':f'backend-release-stage-{COMMIT}-1','expired':False}]}
        self.assertEqual(S.select_release(get,'stage','main')['artifact_id'],'22')

    def test_pagination_across_direct_and_bootstrap_builds_keeps_global_latest_order(self):
        def get(path):
            if path.startswith('actions/workflows/backend-images-publish.yml/'):
                return {'workflow_runs':[self.publication(i) for i in range(400,300,-1)] if path.endswith('&page=1') else [self.publication(200)]}
            if path.startswith('actions/workflows/backend-aws-infrastructure.yml/'):
                return {'workflow_runs':[self.publication(150)]}
            run_id=int(path.split('/')[2])
            prefix='backend-release-v2' if run_id in {150,200} else 'backend-release'
            return {'artifacts':[{'id':run_id*10,'name':f'{prefix}-stage-{COMMIT}-1','expired':False}]}
        self.assertEqual(S.select_release(get,'stage','main',require_operations=True)['run_id'],'200')
