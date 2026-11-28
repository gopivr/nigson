"""Offline first-deployment ordering and fail-closed checks; no AWS credentials used."""
import copy
import importlib.util
import json
from pathlib import Path
import subprocess
import tempfile
import unittest
from unittest.mock import patch

from test_latest_backend import FakeAWS, release as base_release, ACCOUNT, REGION

def release():
    value=base_release();value["components"]["operations"]=dict(value["components"]["identity"]);return value

from test_first_account_pipeline import applied_variables
ROOT=Path(__file__).resolve().parents[3]
spec=importlib.util.spec_from_file_location('runtime_setup',ROOT/'scripts/infra/setup-first-account-runtime.py')
R=importlib.util.module_from_spec(spec);spec.loader.exec_module(R)


def values():
    return applied_variables(region=REGION,cpu_architecture='X86_64',key_administrator_principal_arn=None)


def configuration():
    return {'application_environment':{owner:{'APP_ENV':'stage','NIGSON_SECURITY_ENABLED':'true','NIGSON_DB_USER':'nigson_'+owner+'_app','NIGSON_OIDC_ISSUER':'https://auth.stage.nigson.com/realms/nigson-stage'} for owner in ['commerce','experience','integration']},
            'application_secret_names':{},'provider_egress':{}}


class Pipeline:
    def __init__(self, root, events, applied=None):
        self.root=Path(root);self.work=self.root/'ci';self.bootstrap=self.work/'bootstrap'
        self.events=events;self.applied=applied or values();self.plans={};self.changes=[]
    def infrastructure(self):
        return {'secret_arns':{'identity':{'database_password':f'arn:aws:secretsmanager:{REGION}:{ACCOUNT}:secret:nigson/stage/identity/database_password-abc','tls_key':'unused-legacy-reference'}},'identity_bootstrap_task_definition':f'arn:aws:ecs:{REGION}:{ACCOUNT}:task-definition/nigson-stage-identity-bootstrap:1','identity_security_group_id':'sg-12345678','private_subnet_ids':['subnet-12345678','subnet-87654321'], 'frontend_url':'https://stage.nigson.com','setup_security_group_id':'sg-12345678','setup_task_definitions':{mode:f'arn:aws:ecs:{REGION}:{ACCOUNT}:task-definition/nigson-stage-{mode}-setup:1' for mode in ['database','realm']}}
    def prepare_bootstrap(self):self.work.mkdir();self.events.append('prepare')
    def init(self,*args):self.events.append('init')
    def tf(self,root,*args,capture=False):
        if args[0]=='plan':
            merged={}
            for arg in args:
                if arg.startswith('-var-file='):merged.update(json.loads(Path(arg.split('=',1)[1]).read_text()))
            path=next(arg.split('=',1)[1] for arg in args if arg.startswith('-out='))
            self.plans[path]=merged;self.events.append('plan:'+Path(path).stem)
            return ''
        if args[0]=='show':
            if len(args)==2:
                return json.dumps({'values':{'outputs':{'deployment_configuration':{'value':{'schema_version':1,'variables':self.applied}},'infrastructure':{'value':self.infrastructure()}}}})
            return json.dumps({'resource_changes':self.changes,'planned_values':{'outputs':{'deployment_configuration':{'value':{'variables':self.plans[args[2]]}}}}})
        if args[0]=='apply':self.events.append('apply:'+Path(args[-1]).stem);return ''
        if args[0]=='output':
            if args[-1]=='configuration':
                return json.dumps({'expected_account_id':ACCOUNT,'region':REGION,'environment':'stage','domain_name':'nigson.com','zone_name':'stage.nigson.com','zone_id':'Z1234567890','state_bucket':f'nigson-{ACCOUNT}-{REGION}-terraform-state','state_key':'nigson/first-account/stage/terraform.tfstate'})
            if args[-1]=='infrastructure':return json.dumps(self.infrastructure())
            if args[-1]=='migrations':
                return json.dumps({'environment':'stage','region':REGION,'cluster_arn':f'arn:aws:ecs:{REGION}:{ACCOUNT}:cluster/nigson-stage','private_subnet_ids':['subnet-12345678','subnet-87654321'],'security_group_id':'sg-12345678','task_definition_arns':{owner:f'arn:aws:ecs:{REGION}:{ACCOUNT}:task-definition/nigson-stage-{owner}-migration-BP1:1' for owner in ['commerce','experience','integration']}})
        raise AssertionError(args)


class AWS(FakeAWS):
    def __init__(self,events):
        super().__init__();self.events=events;self.bootstrap=False;self.empty_secret=False;self.bootstrap_exit=0;self.setup=False
    def call(self,aws_service,operation,**kw):
        self.events.append(operation)
        if operation=='describe-services' and not any(event=='apply:runtime-identity' for event in self.events):
            return {'services':[], 'failures':[{'reason':'MISSING'}]}
        if operation=='list-tasks':return {'taskArns':[]}
        if operation=='describe-secret':return {'VersionIdsToStages':{} if self.empty_secret else {'version':['AWSCURRENT']}}
        if operation=='describe-task-definition' and '-setup:' in kw['task_definition']:
            return {'taskDefinition':{'taskDefinitionArn':kw['task_definition'],'containerDefinitions':[{'name':'runtime-setup','image':release()['components']['operations']['image']}]}}
        if operation=='run-task':
            self.bootstrap='identity-bootstrap' in kw['task_definition'];self.setup='-setup:' in kw['task_definition']
            self.events.append('bootstrap' if self.bootstrap else 'setup' if self.setup else 'migration')
        result=super().call(aws_service,operation,**kw)
        if operation=='describe-task-definition':
            td=result['taskDefinition'];td['runtimePlatform']['cpuArchitecture']='ARM64'
            if '-migration-' in td['family']:td['containerDefinitions'][0]['image']=release()['components']['migration']['image']
        if operation=='describe-tasks' and self.setup:
            result['tasks'][0]['containers']=[{'name':'runtime-setup','exitCode':0}]
        if operation=='describe-tasks' and self.bootstrap:
            result['tasks'][0]['containers']=[{'name':'keycloak','exitCode':self.bootstrap_exit}]
        return result
    def wait(self, waiter, **kw):
        if self.setup:
            self.calls.append(('wait',waiter,kw));return
        return super().wait(waiter,**kw)


class RuntimeSetupTests(unittest.TestCase):
    def setUp(self):
        self.tmp=tempfile.TemporaryDirectory();self.addCleanup(self.tmp.cleanup)
        self.root=Path(self.tmp.name);self.events=[];self.aws=AWS(self.events);self.pipeline=Pipeline(self.root,self.events)
        self.config=configuration()
        def derive(applied,infrastructure,selected,aws):
            value=copy.deepcopy(applied);value.update(self.config)
            value.update(publish_task_definitions=True,activate_identity=applied['activate_identity'],activate_applications=False,cpu_architecture='ARM64',
                image_digests={n:v['image'].split('@')[1] for n,v in selected['components'].items() if n!='migration'},migration_image_digest=selected['components']['migration']['image'].split('@')[1])
            return value
        patcher=patch.object(R.defaults,'derive',side_effect=derive);patcher.start();self.addCleanup(patcher.stop)
        patcher=patch.object(R.defaults,'seed_internal');patcher.start();self.addCleanup(patcher.stop)
    def run_setup(self,apply=True,check=None):
        runner=R.RuntimeSetup(self.pipeline,self.aws,release(),apply,realm_check=check or (lambda issuer:self.events.append('realm')))
        runner.run();return runner
    def test_legacy_release_is_refused_before_terraform_initialization(self):
        value=release();del value['components']['operations']
        runner=R.RuntimeSetup(self.pipeline,self.aws,value,False)
        with self.assertRaisesRegex(ValueError,'scan-qualified operations image'):runner.run()
        self.assertEqual(self.events,[])

    def test_templates_migrations_bootstrap_identity_and_app_activation_are_ordered(self):
        runner=self.run_setup()
        self.assertEqual(runner.runner.record['status'],'RUNTIME_ACTIVATED')
        self.assertEqual(self.events.count('migration'),3)
        self.assertEqual(self.events.count('bootstrap'),1)
        positions=[self.events.index(name) for name in ['apply:runtime-templates','migration','bootstrap','apply:runtime-identity','realm','apply:runtime-activation']]
        self.assertEqual(positions,sorted(positions))
        self.assertEqual(self.pipeline.plans[str(self.pipeline.work/'runtime-templates.tfplan')]['frontend_release_id'],'initial')
        self.assertEqual(runner.runner.record['identity_bootstrap']['status'],'SUCCEEDED')
        self.assertEqual(runner.runner.record['identity_bootstrap']['task_definition'].split('/')[-1],'nigson-stage-identity-bootstrap:1')
    def test_plan_is_read_only_and_uses_release_architecture(self):
        runner=self.run_setup(False)
        self.assertEqual(runner.runner.record['status'],'RUNTIME_PLAN_ONLY')
        self.assertFalse(any(x.startswith('apply:') or x in {'run-task','bootstrap','migration'} for x in self.events))
        self.assertEqual(self.pipeline.plans[str(self.pipeline.work/'runtime-activation-preview.tfplan')]['cpu_architecture'],'ARM64')
    def test_unpopulated_secrets_stop_before_migrations_or_service_activation(self):
        self.aws.empty_secret=True
        with self.assertRaisesRegex(ValueError,'Populate these'):self.run_setup()
        self.assertIn('apply:runtime-templates',self.events)
        self.assertNotIn('migration',self.events);self.assertNotIn('apply:runtime-identity',self.events)
    def test_migration_failure_prevents_identity_bootstrap_and_all_activation(self):
        self.aws.fail_receipt=True
        with self.assertRaisesRegex(RuntimeError,'migration failed'):self.run_setup()
        self.assertNotIn('bootstrap',self.events);self.assertNotIn('apply:runtime-identity',self.events)
    def test_migration_timeout_is_unknown_and_not_retried(self):
        self.aws.wait_failure='tasks-stopped'
        with self.assertRaises(RuntimeError):self.run_setup()
        saved=json.loads((self.root/'.build/backend-deploy/deployment.json').read_text())
        self.assertEqual(saved['migration_tasks']['commerce']['status'],'UNKNOWN')
        self.assertEqual(self.events.count('migration'),1);self.assertNotIn('bootstrap',self.events)
    def test_bootstrap_failure_never_activates_identity(self):
        self.aws.bootstrap_exit=1
        with self.assertRaisesRegex(RuntimeError,'bootstrap failed'):self.run_setup()
        self.assertNotIn('apply:runtime-identity',self.events)
    def test_uncertain_bootstrap_launch_is_retained_without_retry_or_activation(self):
        original=self.aws.wait
        def wait(waiter,**kw):
            if self.aws.bootstrap:raise RuntimeError('isolated bootstrap wait timeout')
            return original(waiter,**kw)
        self.aws.wait=wait
        with self.assertRaisesRegex(RuntimeError,'bootstrap wait timeout'):self.run_setup()
        saved=json.loads((self.root/'.build/backend-deploy/deployment.json').read_text())
        self.assertEqual(saved['identity_bootstrap']['status'],'UNKNOWN')
        self.assertEqual(self.events.count('bootstrap'),1)
        self.assertNotIn('apply:runtime-identity',self.events)

    def test_running_identity_node_blocks_offline_bootstrap(self):
        original=self.aws.call
        def call(service,operation,**kw):
            if operation=='list-tasks':return {'taskArns':['isolated-running-node']}
            return original(service,operation,**kw)
        self.aws.call=call
        with self.assertRaisesRegex(ValueError,'nodes are still present'):self.run_setup()
        self.assertNotIn('bootstrap',self.events);self.assertNotIn('apply:runtime-identity',self.events)

    def test_missing_realm_keeps_backend_services_stopped(self):
        def fail(issuer):raise ValueError('realm missing')
        with self.assertRaisesRegex(ValueError,'realm missing'):self.run_setup(check=fail)
        self.assertIn('apply:runtime-identity',self.events);self.assertNotIn('apply:runtime-activation',self.events)
    def test_resuming_partial_identity_does_not_run_bootstrap_again(self):
        applied=values();applied.update(configuration());applied.update(publish_task_definitions=True,activate_identity=True,cpu_architecture='ARM64',image_digests={n:v['image'].split('@')[1] for n,v in release()['components'].items() if n!='migration'},migration_image_digest=release()['components']['migration']['image'].split('@')[1])
        self.pipeline.applied=applied
        self.run_setup()
        self.assertNotIn('bootstrap',self.events)
        self.assertTrue(self.pipeline.plans[str(self.pipeline.work/'runtime-templates.tfplan')]['activate_identity'])
    def test_already_active_runtime_uses_normal_release_workflow(self):
        self.pipeline.applied['activate_applications']=True
        with self.assertRaisesRegex(ValueError,'already activated'):self.run_setup()
        self.assertFalse(any(x.startswith('apply:') for x in self.events))
    def test_phase_or_foundation_override_is_rejected_before_mutation(self):
        for key,value in [('region','eu-west-1'),('frontend_release_id','other'),('product_phase',2)]:
            with self.subTest(key=key):
                baseline=values();resolved=copy.deepcopy(baseline);resolved.update(configuration());resolved[key]=value
                with self.assertRaisesRegex(ValueError,'foundation'):R.validate_inputs(baseline,resolved,release())
    def test_rds_master_or_cross_owner_database_roles_are_not_used_by_apps(self):
        for username in ['postgres','nigson_experience_app']:
            bad=configuration();bad['application_environment']['commerce']['NIGSON_DB_USER']=username
            self.config=bad
            # Isolate each run's persisted receipt/work paths.
            self.pipeline=Pipeline(self.root,self.events)
            if self.pipeline.work.exists():
                import shutil
                shutil.rmtree(self.pipeline.work)
            artifact=self.root/'.build/backend-deploy'
            if artifact.exists():
                import shutil
                shutil.rmtree(artifact)
            with self.assertRaisesRegex(ValueError,'non-privileged database role'):self.run_setup()
        self.assertFalse(any(event.startswith('apply:') for event in self.events))

    def test_secrets_in_reviewed_environment_are_rejected(self):
        bad=configuration();bad['application_environment']['commerce']['PAYSTACK_SECRET_KEY']='isolated-test-key';self.config=bad
        with self.assertRaisesRegex(ValueError,'Secret values'):self.run_setup()
        self.assertFalse(any(x.startswith('apply:') for x in self.events))
    def test_plan_guard_denies_deletions_and_database_changes(self):
        for address,kind,actions in [('module.compute.aws_ecs_service.service["commerce"]','aws_ecs_service',['delete','create']),('module.identity.aws_db_instance.identity','aws_db_instance',['update']),('module.frontend.aws_cloudfront_distribution.frontend','aws_cloudfront_distribution',['update'])]:
            with self.subTest(address=address),self.assertRaises(ValueError):
                R.guard_plan({'resource_changes':[{'address':address,'type':kind,'change':{'actions':actions}}]})
    def test_secret_metadata_is_checked_without_reading_values_and_legacy_tls_is_ignored(self):
        self.run_setup()
        self.assertNotIn('get-secret-value',self.events)
        self.assertEqual(self.events.count('describe-secret'),1)
