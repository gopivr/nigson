#!/usr/bin/env python3
"""Grant portal permissions only to the checked-in local admin fixture identity."""
import json
import os
import uuid
from seed_demo import ROOT, uid, literal, docker as command

ROLES = ('ADMIN', 'CONTENT_EDITOR', 'CONTENT_PUBLISHER', 'SALES', 'OPERATIONS', 'FINANCE', 'ANALYST')


def main():
    if os.environ.get('ENV', 'local') not in ('local', 'test'):
        raise ValueError('Local/test environment required')
    if os.environ.get('DOCKER_HOST') and not os.environ['DOCKER_HOST'].startswith(('unix://', 'npipe://')):
        raise ValueError('Local Unix Docker endpoint required')
    context = json.loads(command('context', 'inspect'))[0]
    if not context['Endpoints']['docker']['Host'].startswith(('unix://', 'npipe://')):
        raise ValueError('Local Unix Docker endpoint required')
    config = json.loads(command('compose', '--profile', 'app', 'config', '--format', 'json'))
    if config.get('name') != 'nigson-local' or any(config['services'][s]['environment'].get('APP_ENV') != 'local' for s in ('commerce', 'postgres', 'keycloak')):
        raise ValueError('Expected local nigson-local project')
    fixtures = json.loads((ROOT / 'tests/fixtures/local-identities.json').read_text())
    fixture = next(user for user in fixtures['subjects'] if user['username'] == 'admin')
    if not fixtures.get('localOnly') or fixtures['identityRealm'] != 'nigson-local':
        raise ValueError('Expected local fixture realm')
    subject = str(uuid.UUID(fixture['id']))
    issuer = config['services']['commerce']['environment']['NIGSON_OIDC_ISSUER']
    base = ('compose', 'exec', '-T', 'postgres', 'psql', '-X', '-qAt', '-U', 'nigson_bootstrap', '-d', 'nigson', '-v', 'ON_ERROR_STOP=1')
    party = command(*base, '-c', f"SELECT id FROM commerce.party WHERE identity_subject={literal(subject)} AND identity_issuer={literal(issuer)} AND status='ACTIVE'")
    if not party:
        raise ValueError('Sign in with the existing local admin account once to provision its party')
    party = str(uuid.UUID(party))
    sql = f"BEGIN;\nSET LOCAL lock_timeout='5s';\nSELECT id FROM commerce.party WHERE id={literal(party)} AND identity_subject={literal(subject)} AND identity_issuer={literal(issuer)} AND status='ACTIVE' FOR UPDATE;\n"
    for role in ROLES:
        sql += f"INSERT INTO commerce.staff_role_assignment(id,created_by,updated_by,party_id,role,granted_by) VALUES ({literal(uid('local-admin-role/' + role))},'local-admin-bootstrap','local-admin-bootstrap',{literal(party)},{literal(role)},{literal(party)}) ON CONFLICT DO NOTHING;\n"
    sql += f"""DO $$ BEGIN IF (SELECT count(DISTINCT role) FROM commerce.staff_role_assignment WHERE party_id={literal(party)} AND revoked_at IS NULL) <> 7 THEN RAISE EXCEPTION 'Local admin role verification failed'; END IF; END $$;
INSERT INTO commerce.audit_record(id,created_by,actor_subject,actor_kind,action,resource_type,resource_id,occurred_at,correlation_id,reason,new_value)
VALUES ({literal(uid('local-admin-grant-audit'))},'local-admin-bootstrap','local-admin-provisioner','SYSTEM','LOCAL_ADMIN_GRANTED','party',{literal(party)},now(),{literal(uid('local-admin-grant-correlation'))},'User requested admin portal access for the existing local admin fixture',{literal({'roles': list(ROLES)})}::jsonb) ON CONFLICT(id) DO NOTHING;
COMMIT;
SELECT role FROM commerce.staff_role_assignment WHERE party_id={literal(party)} AND revoked_at IS NULL ORDER BY role;
"""
    output = command(*base, input=sql)
    if not set(ROLES).issubset(set(output.splitlines())):
        raise RuntimeError('Post-commit active role verification failed')
    print('PASS: existing local admin account has all seven portal business roles, including ADMIN.')
    print('No password or MFA setting changed.')


if __name__ == '__main__':
    main()