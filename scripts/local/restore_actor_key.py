#!/usr/bin/env python3
"""Recover an emulator-only actor signing key after local KMS state loss.

Rotates only the local actor key, retaining sessions and owner data. Restart the
Commerce, Experience and Integration containers after this changes their .env key settings.
"""
import base64
import json
import os
from pathlib import Path
import subprocess
import uuid

ROOT = Path(__file__).resolve().parents[2]


def main():
    env = dict(line.split('=', 1) for line in (ROOT / '.env').read_text().splitlines() if line and not line.startswith('#') and '=' in line)
    if env.get('APP_ENV', 'local') != 'local' or os.environ.get('ENV', 'local') != 'local':
        raise ValueError('LOCAL_ONLY')
    if os.environ.get('DOCKER_HOST') and not os.environ['DOCKER_HOST'].startswith(('unix://', 'npipe://')):
        raise ValueError('LOCAL_DOCKER_REQUIRED')
    if os.environ.get('COMPOSE_PROJECT_NAME', 'nigson-local') != 'nigson-local':
        raise ValueError('LOCAL_PROJECT_REQUIRED')
    endpoint = subprocess.run(['docker', 'context', 'inspect', '--format', '{{json .Endpoints.docker.Host}}'], check=True, capture_output=True, text=True, timeout=15).stdout
    if not json.loads(endpoint).startswith(('unix://', 'npipe://')):
        raise ValueError('LOCAL_DOCKER_REQUIRED')
    region = env.get('NIGSON_AWS_REGION', 'us-east-1')
    def kms(*args):
        result = subprocess.run(['docker', 'compose', '--project-name', 'nigson-local', '--env-file', str(ROOT / '.env'), '--file', str(ROOT / 'docker-compose.yml'), 'exec', '-T', 'localstack', 'awslocal', '--region', region, 'kms', *args], cwd=ROOT, check=True, capture_output=True, text=True, timeout=30)
        return json.loads(result.stdout)
    old = env.get('NIGSON_ACTOR_KMS_KEY')
    if old:
        try:
            found = kms('get-public-key', '--key-id', old)
            if found['PublicKey'] == env.get('NIGSON_ACTOR_PUBLIC_KEY'):
                print('PASS: configured local actor key exists and matches public trust; unchanged')
                return
        except subprocess.CalledProcessError as failure:
            if 'NotFoundException' not in (failure.stderr or ''):
                raise
    key = kms('create-key', '--key-spec', 'ECC_NIST_P256', '--key-usage', 'SIGN_VERIFY', '--description', 'Nigson explicit local testing actor key')['KeyMetadata']['Arn']
    public = kms('get-public-key', '--key-id', key)['PublicKey']
    base64.b64decode(public, validate=True)
    kid = 'local-' + uuid.uuid4().hex[:12]
    changes = {'NIGSON_ACTOR_KMS_KEY': key, 'NIGSON_ACTOR_KEY_ID': kid, 'NIGSON_ACTOR_PUBLIC_KEY': public, 'NIGSON_ACTOR_PUBLIC_KEYS': json.dumps({kid: public}, separators=(',', ':'))}
    lines = (ROOT / '.env').read_text().splitlines()
    remaining = dict(changes)
    for number, line in enumerate(lines):
        name = line.split('=', 1)[0]
        if name in remaining:
            lines[number] = name + '=' + remaining.pop(name)
    lines.extend(name + '=' + value for name, value in remaining.items())
    (ROOT / '.env').write_text('\n'.join(lines) + '\n')
    print('PASS: emulator-only actor key rotated; restart Commerce, Experience and Integration to load matching trust. Database/session data unchanged.')


if __name__ == '__main__':
    main()
