#!/usr/bin/env python3
"""Deploy one scan-qualified image cohort using existing environment ECS templates."""
import argparse
import copy
import hashlib
import json
import os
from pathlib import Path
import re
import subprocess

ROOT = Path(__file__).resolve().parents[2]
DIGEST = r'sha256:[a-f0-9]{64}'


def validate_release(release, environment, account, region, commit, run_id, attempt):
    if environment not in {'stage', 'prod'} or not re.fullmatch(r'[0-9]{12}', account) or not re.fullmatch(r'[a-z]{2}-[a-z]+-[0-9]+', region):
        raise ValueError('Invalid deployment account or region')
    required = {'schema_version', 'environment', 'account_id', 'region', 'architecture',
                'source_commit', 'source_run_id', 'source_run_attempt', 'product_phase', 'components'}
    if not isinstance(release, dict) or set(release) != required or type(release['schema_version']) is not int or release['schema_version'] != 1:
        raise ValueError('Unsupported or incomplete release artifact')
    if not str(run_id).isdigit() or not str(attempt).isdigit():
        raise ValueError('Expected immutable publication run metadata')
    expected = {'environment': environment, 'account_id': account, 'region': region,
                'source_commit': commit, 'source_run_id': str(run_id), 'source_run_attempt': str(attempt)}
    if any(release.get(k) != v for k, v in expected.items()) or not re.fullmatch(r'[a-f0-9]{40}', commit):
        raise ValueError('Release artifact does not match the selected environment/account/publication')
    if release['architecture'] not in {'amd64', 'arm64'} or type(release['product_phase']) is not int or release['product_phase'] not in range(1, 6):
        raise ValueError('Invalid release architecture or product phase')
    components = {'commerce', 'experience', 'integration', 'identity', 'migration'}
    if release['product_phase'] == 5:
        components.add('ai')
    if not isinstance(release['components'], dict) or set(release['components']) != components:
        raise ValueError('Release must contain the complete environment image cohort')
    for component, item in release['components'].items():
        repository = 'commerce' if component == 'migration' else component
        prefix = f'{account}.dkr.ecr.{region}.amazonaws.com/nigson/{environment}/{repository}@'
        if (not isinstance(item, dict) or set(item) != {'image', 'scan_digest', 'critical', 'high'} or
                not isinstance(item['image'], str) or not item['image'].startswith(prefix) or
                not re.fullmatch(DIGEST, item['image'][len(prefix):]) or
                not isinstance(item['scan_digest'], str) or not re.fullmatch(DIGEST, item['scan_digest']) or
                type(item['critical']) is not int or type(item['high']) is not int or item['critical'] != 0 or item['high'] != 0):
            raise ValueError('Unqualified or invalid image in release: ' + component)
    return release


class AWS:
    def call(self, aws_service, operation, **kwargs):
        args = ['aws', aws_service, operation]
        for key, value in kwargs.items():
            if isinstance(value, bool):
                args.append('--' + ('' if value else 'no-') + key.replace('_', '-'))
            else:
                args += ['--' + key.replace('_', '-'), json.dumps(value) if isinstance(value, (dict, list)) else str(value)]
        args += ['--region', os.environ['AWS_REGION'], '--output', 'json', '--no-cli-pager']
        result = subprocess.run(args, capture_output=True, text=True, check=False)
        if result.returncode:
            code = re.search(r'An error occurred \(([A-Za-z0-9]+)\)', result.stderr)
            raise RuntimeError(f'AWS {aws_service}/{operation} failed: ' + (code.group(1) if code else 'REQUEST_FAILED'))
        return json.loads(result.stdout) if result.stdout.strip() else {}

    def wait(self, waiter, **kwargs):
        args = ['aws', 'ecs', 'wait', waiter, '--region', os.environ['AWS_REGION'], '--no-cli-pager']
        for key, value in kwargs.items():
            args += ['--' + key.replace('_', '-'), json.dumps(value) if isinstance(value, list) else str(value)]
        if subprocess.run(args, capture_output=True, check=False).returncode:
            raise RuntimeError('ECS waiter did not confirm ' + waiter)


class Deployer:
    def __init__(self, release, aws, work):
        self.release, self.aws, self.work = release, aws, Path(work)
        self.env, self.account, self.region = (release[k] for k in ('environment', 'account_id', 'region'))
        self.cluster = f'arn:aws:ecs:{self.region}:{self.account}:cluster/nigson-{self.env}'
        self.owners = ['commerce', 'experience', 'integration'] + (['ai'] if release['product_phase'] == 5 else [])
        self.services = ['identity'] + self.owners
        self.record = {'status': 'PREFLIGHT', 'environment': self.env, 'source_commit': release['source_commit'],
                       'source_run_id': release['source_run_id'], 'images': {k: v['image'] for k, v in release['components'].items()}, 'previous_task_definitions': {},
                       'task_definitions': {}, 'migration_tasks': {}, 'rollback': {}}
        self.work.mkdir(parents=True, exist_ok=False, mode=0o700)

    def save(self):
        path = self.work / 'deployment.json'
        with open(path, 'w', opener=lambda p, flags: os.open(p, flags, 0o600)) as out:
            json.dump(self.record, out, indent=2)

    def family(self, service, migration=False):
        return f'nigson-{self.env}-{service}' + (f'-migration-BP{self.release["product_phase"]}' if migration else '')

    def template(self, service, reference, migration=False):
        result = self.aws.call('ecs', 'describe-task-definition', task_definition=reference)
        td = result['taskDefinition']
        expected = self.family(service, migration)
        arn_pattern = rf'arn:aws:ecs:{self.region}:{self.account}:task-definition/{re.escape(expected)}:[0-9]+'
        if td.get('family') != expected or not re.fullmatch(arn_pattern, td.get('taskDefinitionArn', '')):
            raise ValueError('Task template belongs to another environment/family: ' + service)
        if (reference.startswith('arn:') and td['taskDefinitionArn'] != reference) or td.get('status', 'ACTIVE') != 'ACTIVE':
            raise ValueError('Task revision is unavailable for deployment/rollback: ' + service)
        suffix = '-migration' if migration else ''
        for field, kind in [('executionRoleArn', 'execution'), ('taskRoleArn', 'task')]:
            wanted = f'arn:aws:iam::{self.account}:role/nigson-{self.env}-{service}{suffix}-{kind}'
            if td.get(field) != wanted:
                raise ValueError('Unexpected task role in template: ' + service)
        if td.get('networkMode') != 'awsvpc' or set(td.get('requiresCompatibilities', [])) != {'FARGATE'} or td.get('runtimePlatform', {}).get('operatingSystemFamily', 'LINUX') != 'LINUX':
            raise ValueError('Expected an existing Fargate/awsvpc task template')
        payload = {k: copy.deepcopy(v) for k, v in td.items() if k in {
            'family', 'taskRoleArn', 'executionRoleArn', 'networkMode', 'containerDefinitions', 'volumes',
            'placementConstraints', 'requiresCompatibilities', 'cpu', 'memory', 'pidMode', 'ipcMode',
            'proxyConfiguration', 'inferenceAccelerators', 'ephemeralStorage', 'runtimePlatform'}}
        container_name = 'owner-migration' if migration else ('keycloak' if service == 'identity' else service)
        containers = [c for c in payload['containerDefinitions'] if c.get('name') == container_name]
        if len(containers) != 1 or any(c.get('privileged') for c in payload['containerDefinitions']):
            raise ValueError('Invalid primary container or privileged task template')
        primary = containers[0]
        values = {v['name']: v['value'] for v in primary.get('environment', [])}
        if any(values.get(k, self.env) != self.env for k in ['APP_ENV', 'NIGSON_ENVIRONMENT']):
            raise ValueError('Task template runtime environment does not match deployment')
        if migration and (values.get('MIGRATION_OWNER') != service or values.get('MIGRATION_RELEASE') != f'BP{self.release["product_phase"]}'):
            raise ValueError('Migration owner/phase does not match the release')
        if not migration and 'NIGSON_PRODUCT_PHASE' in values and values['NIGSON_PRODUCT_PHASE'] != str(self.release['product_phase']):
            raise ValueError('Provision the approved product phase before deploying its images')
        old_image = primary.get('image', '')
        component = 'migration' if migration else service
        primary['image'] = self.release['components'][component]['image']
        old_arch = payload.get('runtimePlatform', {}).get('cpuArchitecture', 'X86_64')
        arch = {'amd64': 'X86_64', 'arm64': 'ARM64'}[self.release['architecture']]
        payload['runtimePlatform'] = {'operatingSystemFamily': 'LINUX', 'cpuArchitecture': arch}
        payload['tags'] = [{'key': 'Application', 'value': 'nigson'}, {'key': 'Environment', 'value': self.env},
                           {'key': 'SourceCommit', 'value': self.release['source_commit']}]
        reusable = migration and old_image == primary['image'] and old_arch == arch
        return {'previous': td['taskDefinitionArn'], 'payload': payload, 'reuse': reusable}

    def check_scans(self):
        for name, image in self.release['components'].items():
            repository = f'nigson/{self.env}/' + ('commerce' if name == 'migration' else name)
            digest = image['image'].split('@')[1]
            described = self.aws.call('ecr', 'describe-images', repository_name=repository, image_ids=[{'imageDigest': digest}])
            if len(described.get('imageDetails', [])) != 1 or described['imageDetails'][0].get('imageDigest') != digest:
                raise ValueError('Published image is missing: ' + name)
            scan = self.aws.call('ecr', 'describe-image-scan-findings', repository_name=repository,
                                 image_id={'imageDigest': image['scan_digest']})
            findings, status = scan.get('imageScanFindings'), scan.get('imageScanStatus', {}).get('status')
            if not isinstance(findings, dict) or (status != 'COMPLETE' and not (status == 'ACTIVE' and findings.get('imageScanCompletedAt'))):
                raise ValueError('Image does not have a completed current scan: ' + name)
            if scan.get('imageId', {}).get('imageDigest') != image['scan_digest']:
                raise ValueError('Scan response does not match the selected platform image')
            counts = findings.get('findingSeverityCounts') or {}
            if not isinstance(counts, dict) or any(type(counts.get(level, 0)) is not int or counts.get(level, 0) != 0 for level in ['CRITICAL', 'HIGH']):
                raise ValueError('Current image scan blocks deployment: ' + name)

    def preflight(self):
        if self.aws.call('sts', 'get-caller-identity').get('Account') != self.account:
            raise ValueError('AWS identity does not match the selected account')
        self.check_scans()
        states = self.aws.call('ecs', 'describe-services', cluster=self.cluster,
                               services=[self.family(s) for s in self.services])
        if states.get('failures') or len(states.get('services', [])) != len(self.services):
            raise ValueError('One-time runtime setup is incomplete: provision and activate the environment services/task templates first')
        self.current = {s['serviceName']: s for s in states['services']}
        self.targets = {}
        for service in self.services:
            state = self.current.get(self.family(service), {})
            if state.get('status') != 'ACTIVE' or state.get('clusterArn') != self.cluster or state.get('desiredCount', 0) < 1:
                raise ValueError('Service is missing or inactive; complete runtime activation first: ' + service)
            if (len(state.get('deployments', [])) != 1 or state.get('runningCount') != state['desiredCount'] or
                    state.get('pendingCount', 0) != 0 or state['deployments'][0].get('rolloutState') in {'IN_PROGRESS', 'FAILED'}):
                raise ValueError('Wait for the existing service rollout to finish: ' + service)
            current = self.template(service, state['taskDefinition'])
            # Terraform may have registered a newer configuration template while
            # the running service intentionally retains its last promoted revision.
            self.targets[service] = self.template(service, self.family(service))
            self.targets[service]['previous'] = current['previous']
        self.migrations = {owner: self.template(owner, self.family(owner, True), True) for owner in self.owners}
        network = copy.deepcopy(self.current[self.family('commerce')].get('networkConfiguration', {}).get('awsvpcConfiguration', {}))
        subnets = sorted(network.get('subnets', []))
        if not subnets or network.get('assignPublicIp') != 'DISABLED':
            raise ValueError('Expected existing private application subnets')
        described = self.aws.call('ec2', 'describe-subnets', subnet_ids=subnets).get('Subnets', [])
        vpcs = {s['VpcId'] for s in described}
        if len(described) != len(subnets) or len(vpcs) != 1:
            raise ValueError('Application subnets must belong to one VPC')
        groups = self.aws.call('ec2', 'describe-security-groups', filters=[
            {'Name': 'group-name', 'Values': [f'nigson-{self.env}-owner-migrations']},
            {'Name': 'vpc-id', 'Values': sorted(vpcs)}, {'Name': 'tag:Application', 'Values': ['nigson']},
            {'Name': 'tag:Environment', 'Values': [self.env]}]).get('SecurityGroups', [])
        if len(groups) != 1 or groups[0].get('VpcId') not in vpcs:
            raise ValueError('Expected the provisioned owner-migration security group')
        self.migration_network = {'awsvpcConfiguration': {'subnets': subnets, 'securityGroups': [groups[0]['GroupId']], 'assignPublicIp': 'DISABLED'}}
        self.record['previous_task_definitions'] = {s: target['previous'] for s, target in self.targets.items()}
        self.save()

    def register(self, name, target):
        if target['reuse']:
            return target['previous']
        path = self.work / (name + '.json')
        with open(path, 'w', opener=lambda p, flags: os.open(p, flags, 0o600)) as out:
            json.dump(target['payload'], out)
        response = self.aws.call('ecs', 'register-task-definition', cli_input_json='file://' + str(path))
        arn = response['taskDefinition']['taskDefinitionArn']
        family = re.escape(target['payload']['family'])
        if not re.fullmatch(rf'arn:aws:ecs:{self.region}:{self.account}:task-definition/{family}:[0-9]+', arn):
            raise ValueError('Registered task definition does not match the selected environment')
        return arn

    def migrate(self, owner, definition):
        token = hashlib.sha256((self.cluster + owner + definition + self.release['source_commit']).encode()).hexdigest()
        receipt = {'status': 'UNKNOWN', 'client_token': token, 'task_definition': definition}
        self.record['migration_tasks'][owner] = receipt
        self.save()
        response = self.aws.call('ecs', 'run-task', cluster=self.cluster, task_definition=definition,
                                 client_token=token, launch_type='FARGATE', network_configuration=self.migration_network,
                                 started_by='nigson-' + owner + '-' + self.release['source_commit'][:12],
                                 tags=[{'key': 'Application', 'value': 'nigson'}, {'key': 'Environment', 'value': self.env}])
        if response.get('failures') or len(response.get('tasks', [])) != 1:
            raise RuntimeError('Migration launch was not confirmed; inspect receipt before retrying: ' + owner)
        receipt['task_arn'] = response['tasks'][0]['taskArn']
        self.save()
        if not re.fullmatch(rf'arn:aws:ecs:{self.region}:{self.account}:task/nigson-{self.env}/[a-fA-F0-9]+', receipt['task_arn']):
            raise ValueError('Migration task belongs to another cluster')
        self.aws.wait('tasks-stopped', cluster=self.cluster, tasks=[receipt['task_arn']])
        details = self.aws.call('ecs', 'describe-tasks', cluster=self.cluster, tasks=[receipt['task_arn']])
        tasks = details.get('tasks', [])
        if details.get('failures') or len(tasks) != 1 or tasks[0].get('lastStatus') != 'STOPPED':
            raise RuntimeError('Migration outcome is UNKNOWN; inspect receipt before retrying: ' + owner)
        exits = [c.get('exitCode') for c in tasks[0].get('containers', []) if c.get('name') == 'owner-migration']
        if exits != [0]:
            receipt['status'] = 'FAILED'
            self.save()
            raise RuntimeError('Owner migration failed; no service rollout started: ' + owner)
        receipt.update(status='SUCCEEDED', exit_code=0)
        self.save()

    def stable(self, service, definition):
        self.aws.wait('services-stable', cluster=self.cluster, services=[self.family(service)])
        result = self.aws.call('ecs', 'describe-services', cluster=self.cluster, services=[self.family(service)])
        states = result.get('services', [])
        if result.get('failures') or len(states) != 1 or states[0].get('taskDefinition') != definition or states[0].get('status') != 'ACTIVE' or states[0].get('clusterArn') != self.cluster or states[0].get('desiredCount', 0) < 1 or len(states[0].get('deployments', [])) != 1 or states[0]['deployments'][0].get('rolloutState') != 'COMPLETED' or states[0].get('runningCount') != states[0].get('desiredCount') or states[0].get('pendingCount', 0) != 0:
            raise RuntimeError('Service is not healthy on the candidate revision: ' + service)

    def run(self):
        self.preflight()
        changed = []
        try:
            self.record['status'] = 'REGISTERING'
            for owner, target in self.migrations.items():
                self.record['task_definitions'][owner + '-migration'] = self.register(owner + '-migration', target)
            for service, target in self.targets.items():
                self.record['task_definitions'][service] = self.register(service, target)
            self.save()
            for owner in self.owners:
                self.migrate(owner, self.record['task_definitions'][owner + '-migration'])
            self.record['status'] = 'DEPLOYING'
            self.save()
            for service in self.services:
                changed.append(service)
                definition = self.record['task_definitions'][service]
                self.aws.call('ecs', 'update-service', cluster=self.cluster, service=self.family(service), task_definition=definition, force_new_deployment=True)
                self.stable(service, definition)
            self.record['status'] = 'SUCCEEDED'
            self.save()
        except (ValueError, RuntimeError, KeyError, OSError):
            self.record['status'] = 'FAILED'
            for service in reversed(changed):
                if service == 'identity':
                    self.record['rollback'][service] = 'MANUAL_SCHEMA_COMPATIBILITY_REVIEW_REQUIRED'
                    continue
                try:
                    previous = self.targets[service]['previous']
                    self.aws.call('ecs', 'update-service', cluster=self.cluster, service=self.family(service), task_definition=previous, force_new_deployment=True)
                    self.stable(service, previous)
                    self.record['rollback'][service] = 'SUCCEEDED'
                except (ValueError, RuntimeError, KeyError, OSError):
                    self.record['rollback'][service] = 'FAILED_OPERATOR_ACTION_REQUIRED'
            self.save()
            raise
        return self.record


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('environment', choices=['stage', 'prod'])
    parser.add_argument('--receipt', type=Path, required=True)
    parser.add_argument('--source-commit', required=True)
    parser.add_argument('--source-run-id', required=True)
    parser.add_argument('--source-run-attempt', required=True)
    args = parser.parse_args()
    try:
        release = validate_release(json.loads(args.receipt.read_text()), args.environment,
                                   os.environ.get('AWS_EXPECTED_ACCOUNT_ID', ''), os.environ.get('AWS_REGION', ''),
                                   args.source_commit, args.source_run_id, args.source_run_attempt)
        deployer = Deployer(release, AWS(), ROOT / '.build/backend-deploy')
        deployer.run()
        print('Deployment healthy: ' + args.environment + ' / ' + release['source_commit'])
        if os.environ.get('GITHUB_STEP_SUMMARY'):
            with open(os.environ['GITHUB_STEP_SUMMARY'], 'a') as output:
                output.write(f'Backend deployed to **{args.environment}** from image publication `{args.source_run_id}` / `{args.source_commit}`.\n')
    except (ValueError, RuntimeError, KeyError, OSError) as error:
        raise SystemExit(str(error)) from None
