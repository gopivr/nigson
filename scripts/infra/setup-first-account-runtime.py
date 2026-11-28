#!/usr/bin/env python3
"""Guarded first staging runtime setup from reviewed Terraform inputs and a release receipt."""
import argparse
import importlib.util
import json
import os
from pathlib import Path
import re
import urllib.request

ROOT = Path(__file__).resolve().parents[2]
def load(name, filename):
    spec = importlib.util.spec_from_file_location(name, ROOT / 'scripts/infra' / filename)
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module
infra = load('runtime_infra', 'first-account-pipeline.py')
deploy = load('runtime_deploy', 'deploy-latest-backend.py')
defaults = load('runtime_defaults', 'runtime-from-state.py')
realm_policy = load('runtime_realm_policy', '../identity/realm_policy.py')


def validate_inputs(applied, resolved, release):
    if applied is None:
        raise ValueError('Apply foundation infrastructure first to record its configuration')
    if applied['activate_applications']:
        raise ValueError('Applications are already activated; use Backend — Deploy Release')
    allowed = {'key_administrator_principal_arn', 'application_environment', 'application_secret_names', 'provider_egress', 'cpu_architecture',
               'publish_task_definitions', 'activate_identity', 'activate_applications', 'image_digests', 'migration_image_digest'}
    if set(applied) != set(resolved) or any(applied[k] != resolved[k] for k in applied.keys() - allowed):
        raise ValueError('Runtime configuration changes foundation/account/phase/frontend inputs; use the infrastructure workflow for those changes')
    if release['product_phase'] != resolved['product_phase'] or resolved['cpu_architecture'] != {'arm64':'ARM64','amd64':'X86_64'}[release['architecture']]:
        raise ValueError('Runtime phase/architecture must match the selected publication')
    owners = ['commerce', 'experience', 'integration'] + (['ai'] if release['product_phase'] == 5 else [])
    settings = resolved['application_environment']
    if not isinstance(settings, dict) or set(settings) != set(owners):
        raise ValueError('Reviewed runtime configuration must provide every owner environment')
    for owner, values in settings.items():
        if not isinstance(values, dict) or values.get('APP_ENV') != 'stage' or values.get('NIGSON_SECURITY_ENABLED') != 'true':
            raise ValueError('Secure staging runtime settings are required for ' + owner)
        if values.get('NIGSON_DB_USER', values.get('SPRING_DATASOURCE_USERNAME')) != 'nigson_' + owner + '_app':
            raise ValueError('Runtime must use its own non-privileged database role: ' + owner)
        if any(k in {'AWS_ACCESS_KEY_ID','AWS_SECRET_ACCESS_KEY','AWS_SESSION_TOKEN'} or re.search(r'(?:PASSWORD|SECRET(?:_KEY)?|TOKEN|API_KEY|PRIVATE_KEY(?:_PKCS8_BASE64)?|LOCAL_KEY|CREDENTIALS?)$', k, re.I) for k in values):
            raise ValueError('Secret values must be referenced through application_secret_names, not application_environment')
    names = resolved['application_secret_names']
    if not isinstance(names, dict) or not set(names).issubset(owners) or any(
            not isinstance(items, list) or any(not isinstance(name, str) or not re.fullmatch(r'[A-Z][A-Z0-9_]*', name) for name in items)
            for items in names.values()):
        raise ValueError('application_secret_names must contain owner-scoped environment variable names, never secret values')
    if applied['activate_identity'] and (applied['image_digests'] != resolved['image_digests'] or applied['migration_image_digest'] != resolved['migration_image_digest']):
        raise ValueError('Partial identity activation must resume the same release; inspect schema compatibility before changing images')
    if not resolved['publish_task_definitions'] or resolved['activate_applications'] or resolved['activate_identity'] != applied['activate_identity']:
        raise ValueError('Template phase must preserve identity state and leave applications stopped')
    return resolved


# Runtime setup never replaces/deletes infrastructure or updates the database instance.
def guard_plan(plan):
    for change in plan.get('resource_changes', []):
        actions = change['change']['actions']
        if actions in [['no-op'], ['read']]:
            continue
        address = change['address']
        if 'delete' in actions or not address.startswith(('module.compute.', 'module.identity.', 'module.migrations[0].', 'module.secrets.', 'aws_iam_role.setup_execution[', 'aws_iam_role.setup_task[', 'aws_iam_role_policy.setup_execution[', 'aws_ecs_task_definition.setup[')):
            raise ValueError('Runtime setup refuses deletion/replacement or unrelated infrastructure changes: ' + address)
        if change.get('type') in {'aws_db_instance', 'aws_kms_key', 'aws_kms_alias', 'aws_ecr_repository'}:
            raise ValueError('Runtime setup refuses foundational resource changes: ' + address)


def check_realm(issuer):
    # Validate the public route and realm over real, certificate-verified HTTPS.
    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            return None
    try:
        opener = urllib.request.build_opener(NoRedirect())
        with opener.open(issuer + '/.well-known/openid-configuration', timeout=20) as result:
            content = result.read(65537)
        if len(content) > 65536 or json.loads(content).get('issuer') != issuer:
            raise ValueError('issuer mismatch')
    except Exception:
        raise ValueError('Keycloak is running but the staging realm is not ready. Configure it with scripts/identity/provision.py through the private admin endpoint, then resume the same release; applications remain stopped') from None


class RuntimeSetup:
    def __init__(self, pipeline, aws, release, apply, realm_check=check_realm):
        self.pipeline, self.aws, self.release, self.apply = pipeline, aws, release, apply
        self.platform = pipeline.root / 'infrastructure/terraform/aws/first-account'
        self.realm_check = realm_check
        self.runner = None

    def plan(self, values, label, apply=False, reviewed=None):
        varfile = self.pipeline.work / (label + '.tfvars.json')
        with open(varfile, 'w', opener=lambda path, flags: os.open(path, flags, 0o600)) as output:
            json.dump(values, output)
        plan = self.pipeline.work / (label + '.tfplan')
        args = ['plan', '-input=false', '-lock-timeout=5m']
        if reviewed:
            # Preserve applied foundation values, allow only reviewed runtime changes,
            # then bind deployment flags and images to the immutable release cohort.
            baseline = self.pipeline.work / 'applied.tfvars.json'
            baseline.write_text(json.dumps(self.applied)); baseline.chmod(0o600)
            args += ['-var-file=' + str(baseline), '-var-file=' + str(reviewed)]
        args += ['-var-file=' + str(varfile), '-out=' + str(plan)]
        # Do not print raw plans/configuration; validate before emitting safe addresses.
        self.pipeline.tf(self.platform, *args, capture=True)
        document = json.loads(self.pipeline.tf(self.platform, 'show', '-json', str(plan), capture=True))
        guard_plan(document)
        for change in document.get('resource_changes', []):
            if change['change']['actions'] != ['no-op']:
                print('/'.join(change['change']['actions']) + ': ' + change['address'])
        if apply:
            self.pipeline.tf(self.platform, 'apply', '-input=false', '-lock-timeout=5m', str(plan), capture=True)
        return document

    def check_secret_versions(self, infrastructure):
        missing = []
        for owner, entries in infrastructure['secret_arns'].items():
            for name, arn in entries.items():
                if owner == 'identity' and name in {'tls_key','tls_certificate'}:
                    continue  # Generated target TLS never consumes these legacy containers.
                prefix = f'arn:aws:secretsmanager:{self.release["region"]}:{self.release["account_id"]}:secret:nigson/stage/{owner}/'
                if not isinstance(arn, str) or not arn.startswith(prefix):
                    raise ValueError('Unexpected runtime secret location')
                metadata = self.aws.call('secretsmanager', 'describe-secret', secret_id=arn)
                if not any('AWSCURRENT' in stages for stages in metadata.get('VersionIdsToStages', {}).values()):
                    missing.append(owner + '/' + name)
        if missing:
            raise ValueError('Task templates are provisioned. Populate these Secrets Manager entries, then resume runtime-apply: ' + ', '.join(sorted(missing)))

    def run_setup_task(self, infrastructure, mode):
        import base64
        import hashlib
        import zlib
        runner=self.runner
        definition=infrastructure['setup_task_definitions'][mode]
        expected=f'nigson-stage-{mode}-setup'
        if not re.fullmatch(rf'arn:aws:ecs:{runner.region}:{runner.account}:task-definition/{expected}:[0-9]+',definition):
            raise ValueError('Unexpected runtime setup task ARN')
        td=self.aws.call('ecs','describe-task-definition',task_definition=definition)['taskDefinition']
        containers=td.get('containerDefinitions',[])
        if (td.get('taskDefinitionArn')!=definition or len(containers)!=1 or containers[0].get('name')!='runtime-setup'
                or containers[0].get('image')!=self.release['components']['operations']['image']):
            raise ValueError('Runtime setup task is not bound to the selected operations image')
        overrides={}
        if mode=='realm':
            policy=realm_policy.realm_policy('nigson-stage',infrastructure['frontend_url']+'/bff/callback',self.release['product_phase'],self.applied['policy_revision'],'stage',self.applied['log_retention_days']*86400)
            encoded=base64.b64encode(zlib.compress(json.dumps(policy).encode())).decode()
            overrides={'containerOverrides':[{'name':'runtime-setup','environment':[{'name':'SETUP_REALM_POLICY','value':encoded}]}]}
        token=hashlib.sha256((definition+self.release['source_commit']+json.dumps(overrides,sort_keys=True)).encode()).hexdigest()
        receipt={'status':'UNKNOWN','task_definition':definition,'client_token':token}
        runner.record.setdefault('setup_tasks',{})[mode]=receipt;runner.save()
        response=self.aws.call('ecs','run-task',cluster=runner.cluster,task_definition=definition,client_token=token,
            launch_type='FARGATE',enable_execute_command=False,
            network_configuration={'awsvpcConfiguration':{'subnets':infrastructure['private_subnet_ids'],
                'securityGroups':[infrastructure['setup_security_group_id']],'assignPublicIp':'DISABLED'}},**({'overrides':overrides} if overrides else {}))
        if response.get('failures') or len(response.get('tasks',[]))!=1:
            raise RuntimeError('Runtime '+mode+' setup launch is UNKNOWN; inspect the receipt before retrying')
        receipt['task_arn']=response['tasks'][0]['taskArn'];runner.save()
        if not re.fullmatch(rf'arn:aws:ecs:{runner.region}:{runner.account}:task/nigson-stage/[a-fA-F0-9]+',receipt['task_arn']):
            raise ValueError('Runtime setup task belongs to another cluster')
        self.aws.wait('tasks-stopped',cluster=runner.cluster,tasks=[receipt['task_arn']])
        details=self.aws.call('ecs','describe-tasks',cluster=runner.cluster,tasks=[receipt['task_arn']])
        tasks=details.get('tasks',[])
        if details.get('failures') or len(tasks)!=1 or tasks[0].get('lastStatus')!='STOPPED':
            raise RuntimeError('Runtime '+mode+' setup is UNKNOWN; inspect the receipt before retrying')
        exits=[container.get('exitCode') for container in tasks[0].get('containers',[]) if container.get('name')=='runtime-setup']
        if len(exits)!=1 or type(exits[0]) is not int:
            raise RuntimeError('Runtime '+mode+' exit status is UNKNOWN')
        receipt['status']='SUCCEEDED' if exits[0]==0 else 'FAILED';runner.save()
        if exits[0]!=0:raise RuntimeError('Runtime '+mode+' setup failed; inspect its private task logs; no automatic retry')

    def run_bootstrap(self, infrastructure):
        runner = self.runner
        if self.applied['activate_identity']:
            return
        # Keycloak requires every server node stopped before offline bootstrap.
        response = self.aws.call('ecs', 'describe-services', cluster=runner.cluster, services=[runner.family('identity')])
        if any(failure.get('reason') != 'MISSING' for failure in response.get('failures', [])):
            raise ValueError('Cannot establish whether identity is stopped; bootstrap refused')
        if any(service.get('runningCount', 0) or service.get('pendingCount', 0) or service.get('desiredCount', 0) for service in response.get('services', [])):
            raise ValueError('Stop all identity service tasks before the initial offline administrator bootstrap')
        if self.aws.call('ecs', 'list-tasks', cluster=runner.cluster, family=runner.family('identity'), desired_status='RUNNING').get('taskArns'):
            raise ValueError('Identity nodes are still present; offline bootstrap refused')
        definition = infrastructure['identity_bootstrap_task_definition']
        if not re.fullmatch(rf'arn:aws:ecs:{runner.region}:{runner.account}:task-definition/nigson-stage-identity-bootstrap:[0-9]+', definition or ''):
            raise ValueError('Provisioned identity bootstrap task does not match staging')
        receipt = {'status':'UNKNOWN','task_definition':definition}
        runner.record['identity_bootstrap'] = receipt; runner.save()
        # Stable business token across retries. On uncertainty inspect the task before rerun.
        import hashlib
        token = hashlib.sha256((runner.cluster + definition + self.release['source_commit']).encode()).hexdigest()
        receipt['client_token'] = token; runner.save()
        response = self.aws.call('ecs', 'run-task', cluster=runner.cluster, task_definition=definition,
            client_token=token, launch_type='FARGATE', enable_execute_command=False, network_configuration={'awsvpcConfiguration': {
                'subnets':infrastructure['private_subnet_ids'], 'securityGroups':[infrastructure['identity_security_group_id']], 'assignPublicIp':'DISABLED'}})
        if response.get('failures') or len(response.get('tasks', [])) != 1:
            raise RuntimeError('Identity bootstrap launch is UNKNOWN; inspect its receipt before retrying')
        receipt['task_arn'] = response['tasks'][0]['taskArn']; runner.save()
        if not re.fullmatch(rf'arn:aws:ecs:{runner.region}:{runner.account}:task/nigson-stage/[a-fA-F0-9]+', receipt['task_arn']):
            raise ValueError('Unexpected identity bootstrap task ARN')
        self.aws.wait('tasks-stopped', cluster=runner.cluster, tasks=[receipt['task_arn']])
        details = self.aws.call('ecs','describe-tasks',cluster=runner.cluster,tasks=[receipt['task_arn']])
        tasks = details.get('tasks', [])
        if details.get('failures') or len(tasks) != 1 or tasks[0].get('lastStatus') != 'STOPPED':
            raise RuntimeError('Identity bootstrap outcome is UNKNOWN; inspect receipt before retrying')
        exits = [c.get('exitCode') for c in tasks[0].get('containers',[]) if c.get('name') == 'keycloak']
        if len(exits) != 1 or type(exits[0]) is not int:
            raise RuntimeError('Identity bootstrap outcome is UNKNOWN; inspect the task before retrying')
        if exits != [0]:
            receipt['status'] = 'FAILED'; runner.save()
            raise RuntimeError('Identity bootstrap failed; services remain stopped. Verify the identity database role/password and bootstrap credentials')
        receipt['status'] = 'SUCCEEDED'; runner.save()

    def run(self):
        if 'operations' not in self.release['components']:
            raise ValueError('Publish a new complete image release including the scan-qualified operations image before initial setup')
        self.pipeline.prepare_bootstrap()
        bootstrap = json.loads(self.pipeline.tf(self.pipeline.bootstrap, 'output', '-json', 'configuration', capture=True))
        inputs, backend = infra.inputs_module.render(bootstrap)
        if inputs['expected_account_id'] != self.release['account_id'] or inputs['region'] != self.release['region'] or inputs['environment'] != 'stage':
            raise ValueError('Runtime bootstrap state does not match the selected staging release')
        backend_file = self.pipeline.work / 'runtime-backend.hcl'; backend_file.write_text(backend)
        self.pipeline.init(self.platform, '-reconfigure', '-backend-config=' + str(backend_file))
        state = json.loads(self.pipeline.tf(self.platform, 'show', '-json', capture=True))
        self.applied = infra.deployment_configuration(state, self.release['account_id'], self.release['region'])
        if self.applied is None:
            raise ValueError('Apply foundation infrastructure first')
        if self.applied['activate_applications']:
            raise ValueError('Applications already activated; use Backend — Deploy Release')
        infrastructure = state['values']['outputs']['infrastructure']['value']
        self.runner = deploy.Deployer(self.release,self.aws,self.pipeline.root/'.build/backend-deploy')
        runner = self.runner
        try:
            if self.aws.call('sts','get-caller-identity').get('Account') != self.release['account_id']:
                raise ValueError('AWS identity does not match release account')
            runner.check_scans()
            resolved = defaults.derive(self.applied, infrastructure, self.release, self.aws)
            values = validate_inputs(self.applied,resolved,self.release)
            final = dict(values,activate_identity=True,activate_applications=True)
            self.plan(final,'runtime-activation-preview')
            if not self.apply:
                runner.record['status']='RUNTIME_PLAN_ONLY'; return
            self.plan(values,'runtime-templates',apply=True)
            infrastructure = json.loads(self.pipeline.tf(self.platform,'output','-json','infrastructure',capture=True))
            defaults.seed_internal(infrastructure, self.release, self.aws)
            self.check_secret_versions(infrastructure)
            self.run_setup_task(infrastructure, "database")
            network = json.loads(self.pipeline.tf(self.platform,'output','-json','migrations',capture=True))
            if (network['cluster_arn'] != runner.cluster or network['environment'] != 'stage' or network['region'] != runner.region
                    or set(network['task_definition_arns']) != set(runner.owners)):
                raise ValueError('Migration network/output does not match the selected release')
            runner.migration_network={'awsvpcConfiguration':{'subnets':network['private_subnet_ids'],'securityGroups':[network['security_group_id']],'assignPublicIp':'DISABLED'}}
            for owner in runner.owners:
                template=runner.template(owner,network['task_definition_arns'][owner],True)
                if not template['reuse']:
                    raise ValueError('Provisioned migration template does not match the selected release')
                runner.migrate(owner,template['previous'])
            self.run_bootstrap(infrastructure)
            self.plan(dict(values,activate_identity=True),'runtime-identity',apply=True)
            runner.stable('identity',runner.template('identity',runner.family('identity'))['previous'])
            self.run_setup_task(infrastructure, "realm")
            self.realm_check('https://auth.stage.nigson.com/realms/nigson-stage')
            self.plan(final,'runtime-activation',apply=True)
            for owner in runner.owners:
                runner.stable(owner,runner.template(owner,runner.family(owner))['previous'])
            runner.record['status']='RUNTIME_ACTIVATED'
        except (ValueError,RuntimeError,KeyError,OSError):
            runner.record['status']='RUNTIME_SETUP_STOPPED_OPERATOR_ACTION_REQUIRED'
            raise
        finally:
            runner.save()


def detect_first_runtime(aws, release):
    owners=['commerce','experience','integration']+(['ai'] if release['product_phase']==5 else [])
    cluster=f'arn:aws:ecs:{release["region"]}:{release["account_id"]}:cluster/nigson-{release["environment"]}'
    expected={f'nigson-{release["environment"]}-{owner}' for owner in owners}
    response=aws.call('ecs','describe-services',cluster=cluster,services=sorted(expected))
    if any(item.get('reason')!='MISSING' for item in response.get('failures',[])):
        raise ValueError('Unable to determine initial runtime state')
    found={item['serviceName'] for item in response.get('services',[]) if item.get('status')=='ACTIVE'}
    if found-expected or (found and found!=expected):
        raise ValueError('Partially missing application services require inspection; automatic initial provisioning refused')
    required=not found
    if required and release['environment']!='stage':raise ValueError('Automatic initial provisioning targets the first-account staging state only')
    return required


if __name__ == '__main__':
    os.umask(0o077)
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action',choices=['detect','runtime-plan','runtime-apply'])
    parser.add_argument('--receipt',type=Path,required=True)
    args=parser.parse_args()
    try:
        release=deploy.validate_release(json.loads(args.receipt.read_text()),'stage',os.environ.get('AWS_EXPECTED_ACCOUNT_ID',''),os.environ.get('AWS_REGION',''),
            os.environ.get('SOURCE_COMMIT',''),os.environ.get('SOURCE_RUN_ID',''),os.environ.get('SOURCE_RUN_ATTEMPT',''))
        if args.action=='detect':
            required=detect_first_runtime(deploy.AWS(),release)
            with open(os.environ['GITHUB_OUTPUT'],'a') as output:output.write('required='+str(required).lower()+'\n')
            raise SystemExit(0)
        pipeline=infra.Pipeline(release['account_id'],release['region'],'plan')
        RuntimeSetup(pipeline,deploy.AWS(),release,args.action=='runtime-apply').run()
        print('Runtime activation completed' if args.action=='runtime-apply' else 'Runtime plan completed; no changes applied')
    except (ValueError,RuntimeError,KeyError,OSError) as error:
        raise SystemExit(str(error)) from None
