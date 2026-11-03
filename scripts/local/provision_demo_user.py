#!/usr/bin/env python3
"""Create a local-only validation login owning the seeded demo party.

Uses the existing local registration service account for Keycloak administration;
passwords and tokens are never printed. Grants only declared application roles.
"""
import json
import os
from pathlib import Path
import subprocess
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid

ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/local'))
from seed_demo import uid, literal, OVERRIDES

USERNAME = 'demo.validator@example.invalid'
ROLES = ('ADMIN', 'CONTENT_EDITOR', 'CONTENT_PUBLISHER', 'SALES', 'OPERATIONS', 'FINANCE', 'ANALYST')


def command(*args, input=None):
    result = subprocess.run(['docker', *args], cwd=ROOT, input=input, text=True, capture_output=True, timeout=60)
    if result.returncode:
        raise RuntimeError('Local Docker command failed: ' + result.stderr.strip())
    return result.stdout.strip()


def main():
    # Obtain effective Compose settings without printing its sensitive config.
    if os.environ.get('DOCKER_HOST') and not os.environ['DOCKER_HOST'].startswith('unix://'):
        raise ValueError('Local Unix Docker endpoint required')
    context = json.loads(command('context', 'inspect'))[0]
    if not context['Endpoints']['docker']['Host'].startswith('unix://'):
        raise ValueError('Local Unix Docker endpoint required')
    config = json.loads(command('compose', '--profile', 'app', 'config', '--format', 'json'))
    services = config['services']
    if os.environ.get('ENV', 'local') not in ('local', 'test') or any(
        services[s]['environment'].get('APP_ENV') != 'local' for s in ('commerce', 'keycloak', 'postgres')):
        raise ValueError('Local Compose environment required')
    if config.get('name') != 'nigson-local':
        raise ValueError('Expected nigson-local Compose project')
    values = dict(line.split('=', 1) for line in (ROOT / '.env').read_text().splitlines() if '=' in line and not line.startswith('#'))
    password = values.get('KEYCLOAK_FIXTURE_PASSWORD')
    if not password or len(password) < 12:
        raise ValueError('Local KEYCLOAK_FIXTURE_PASSWORD required (12+ characters)')
    port = int(services['keycloak']['ports'][0]['published'])
    base = f'http://127.0.0.1:{port}'

    class NoRedirect(urllib.request.HTTPRedirectHandler):
        def redirect_request(self, *args, **kwargs):
            raise ValueError('Local identity redirect refused')
    http = urllib.request.build_opener(urllib.request.ProxyHandler({}), NoRedirect())

    def request(path, method='GET', data=None, token=None, form=False):
        body = None if data is None else (urllib.parse.urlencode(data) if form else json.dumps(data)).encode()
        headers = {'Content-Type': 'application/x-www-form-urlencoded' if form else 'application/json'}
        if token:
            headers['Authorization'] = 'Bearer ' + token
        try:
            with http.open(urllib.request.Request(base + path, data=body, headers=headers, method=method), timeout=10) as response:
                raw = response.read(524289)
                if len(raw) > 524288:
                    raise ValueError('Oversized identity response')
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            raise RuntimeError(f'Local identity operation refused (HTTP {error.code})') from None

    env = services['commerce']['environment']
    token = request('/realms/nigson-local/protocol/openid-connect/token', 'POST', {
        'grant_type': 'client_credentials', 'client_id': env['NIGSON_REGISTRATION_CLIENT_ID'],
        'client_secret': env['NIGSON_REGISTRATION_CLIENT_SECRET']}, form=True)['access_token']
    prefix = '/admin/realms/nigson-local/users'
    users = request(prefix + '?' + urllib.parse.urlencode({'username': USERNAME, 'exact': 'true'}), token=token)
    created = not users
    if not users:
        request(prefix, 'POST', {'username': USERNAME, 'email': USERNAME,
            'firstName': 'Demo', 'lastName': 'Validator', 'enabled': True, 'emailVerified': True,
            'attributes': {'nigson.local.fixture': ['demo-validator-v1']},
            'credentials': [{'type': 'password', 'value': password, 'temporary': False}]}, token=token)
        users = request(prefix + '?' + urllib.parse.urlencode({'username': USERNAME, 'exact': 'true'}), token=token)
    if len(users) != 1:
        raise ValueError('Validation identity ambiguous')
    user = request(prefix + '/' + users[0]['id'], token=token)
    identity_state = ROOT / '.build/demo-validator-identity.json'
    known_id = json.loads(identity_state.read_text())['id'] if identity_state.exists() else None
    if (not created and known_id != user['id']) or not user.get('enabled') or user.get('email') != USERNAME or user.get('firstName') != 'Demo' or user.get('lastName') != 'Validator':
        raise ValueError('Existing identity is not this local demo fixture; refused overwrite')
    subject = str(uuid.UUID(user['id']))
    identity_state.parent.mkdir(exist_ok=True)
    identity_state.write_text(json.dumps({'id': subject, 'username': USERNAME}) + '\n')
    party = uid('commerce.party')
    issuer = env['NIGSON_OIDC_ISSUER']
    # Refuse reuse of a genuine/non-demo party, or a pre-existing subject mapping.
    sql = f"""BEGIN;
SET LOCAL lock_timeout='5s';
DO $$ BEGIN
 IF NOT EXISTS (SELECT 1 FROM commerce.party WHERE id={literal(party)} AND created_by='demo-seed-v1'
 AND identity_subject IN ('demo-only-no-login',{literal(subject)})) THEN
 RAISE EXCEPTION 'Seeded demo party missing or no longer eligible'; END IF;
END $$;
UPDATE commerce.party SET identity_subject={literal(subject)},identity_issuer={literal(issuer)},
 email={literal(USERNAME)},display_name='Demo Validator',email_verified=true,
 updated_by='demo-validator-bootstrap',updated_at=now(),version=version+1 WHERE id={literal(party)}
 AND (identity_subject,identity_issuer,email,display_name,email_verified) IS DISTINCT FROM
 ({literal(subject)},{literal(issuer)},{literal(USERNAME)},'Demo Validator',true);
"""
    sql += f"UPDATE commerce.sales_order SET address_snapshot={literal(OVERRIDES['sales_order']['address_snapshot'])}::jsonb WHERE created_by='demo-seed-v1' AND address_snapshot='{{}}'::jsonb;\n"
    for role in ROLES:
        sql += f"""INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by)
VALUES ({literal(uid('demo-validator-role/' + role))},'demo-validator-bootstrap','demo-validator-bootstrap',
{literal(party)},{literal(role)},{literal(party)}) ON CONFLICT (id) DO NOTHING;
"""
    sql += f"""INSERT INTO commerce.audit_record(id,created_by,actor_subject,actor_kind,action,resource_type,resource_id,occurred_at,correlation_id,reason)
VALUES ({literal(uid('demo-validator-provision-audit'))},'demo-validator-bootstrap','local-demo-provisioner','SYSTEM',
'DEMO_VALIDATOR_PROVISION','party',{literal(party)},now(),{literal(uid('demo-validator-provision-correlation'))},
'User-authorized local validation identity and seven application staff grants') ON CONFLICT (id) DO NOTHING;
COMMIT;
SELECT role FROM commerce.staff_role_assignment WHERE party_id={literal(party)} AND revoked_at IS NULL ORDER BY role;
"""
    output = command('compose', 'exec', '-T', 'postgres', 'psql', '-X', '-qAt', '-U', 'nigson_bootstrap', '-d', 'nigson', '-v', 'ON_ERROR_STOP=1', input=sql)
    if set(output.splitlines()) != set(ROLES):
        raise RuntimeError('Active business role verification failed')
    # Experience-owned synthetic rows get the same external party identifier;
    # no cross-owner join/FK or application runtime query is introduced.
    experience_sql = 'BEGIN;\n'
    for table in ('lead', 'communication_destination', 'consent_event'):
        experience_sql += f"UPDATE experience.{table} SET party_id={literal(party)} WHERE created_by='demo-seed-v1' AND party_id IS DISTINCT FROM {literal(party)};\n"
    experience_sql += 'COMMIT;\n'
    command('compose', 'exec', '-T', 'postgres', 'psql', '-X', '-qAt', '-U', 'nigson_bootstrap', '-d', 'nigson', '-v', 'ON_ERROR_STOP=1', input=experience_sql)
    print('PASS: local validation identity created/matched; demo party linked; all seven application roles verified.')
    print('Username: ' + USERNAME)
    print('Password: use KEYCLOAK_FIXTURE_PASSWORD from local .env (not printed).')
    print('Party ID: ' + party)
    print('Internal service authentication and recent MFA requirements remain enforced.')


if __name__ == '__main__':
    main()
