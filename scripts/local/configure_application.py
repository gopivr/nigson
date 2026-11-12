"""Configure local Keycloak/BFF trust only; never populate business or provider data."""
import base64
import ipaddress
import json
import os
from pathlib import Path
import secrets
import subprocess

ROOT = Path(__file__).resolve().parents[2]


def configure():
    target = ROOT / '.env'
    values = dict(line.split('=', 1) for line in target.read_text().splitlines()
                  if '=' in line and not line.lstrip().startswith('#'))
    if values.get('APP_ENV', 'local') != 'local' or os.environ.get('ENV', 'local') != 'local' or os.environ.get('COMPOSE_PROJECT_NAME', 'nigson-local') != 'nigson-local':
        raise ValueError('Local application configuration requires APP_ENV=local and the nigson-local project')
    container = json.loads(subprocess.run(['docker', 'inspect', 'nigson-local-postgres-1'], check=True, capture_output=True, text=True).stdout)[0]
    networks = container['NetworkSettings']['Networks']
    ranges = ['127.0.0.1/32']
    for name in networks:
        network = json.loads(subprocess.run(['docker', 'network', 'inspect', name], check=True, capture_output=True, text=True).stdout)[0]
        for config in network['IPAM']['Config']:
            subnet = config.get('Subnet')
            if subnet and ipaddress.ip_network(subnet).is_private:
                ranges.append(subnet)
    realm = json.loads((ROOT / 'infrastructure/docker/keycloak/nigson-local-realm.json').read_text())
    client = next(row for row in realm['clients'] if row['clientId'] == 'nigson-commerce')
    changes = {'NIGSON_SECURITY_ENABLED': 'true'}
    defaults = {'NIGSON_PROVIDERS_ENABLED': 'false', 'NIGSON_PRIVATE_PEER_CIDRS': ','.join(ranges),
                'NIGSON_COMMERCE_WORKLOAD_SECRET': client['secret'],
                'NIGSON_SESSION_KEY_REVISION': 'local-1'}
    if not values.get('NIGSON_SESSION_LOCAL_KEY') and not values.get('NIGSON_SESSION_WRAPPED_KEY'):
        defaults['NIGSON_SESSION_LOCAL_KEY'] = base64.b64encode(secrets.token_bytes(32)).decode()
    changes.update({key: value for key, value in defaults.items() if not values.get(key)})
    lines = target.read_text().splitlines()
    remaining = dict(changes)
    for index, line in enumerate(lines):
        key = line.split('=', 1)[0]
        if key in remaining:
            lines[index] = key + '=' + remaining.pop(key)
    lines.extend(key + '=' + value for key, value in remaining.items())
    target.write_text('\n'.join(lines) + '\n')
    subprocess.run(['python3', str(ROOT / 'scripts/local/restore_actor_key.py')], cwd=ROOT, check=True)
    print('Local application identity enabled. Existing keys and business data preserved; no provider configuration changed.')


if __name__ == '__main__':
    configure()
