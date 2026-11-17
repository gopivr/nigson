#!/usr/bin/env python3
"""Local referral-code smoke (API-063 GET / API-161 POST).

Creates a temporary CUSTOMER in the local realm, completes the real BFF OIDC
flow, proves GET returns the documented 404 before creation, creates the code
through POST, then proves GET/200 and idempotent replay return the same stable
code. Requires `make run-core` and a configured
`NIGSON_REWARD_REFERRAL_BASE_URL`. Removes the temporary identity and its
referral rows. Exits non-zero on any failed step.
"""

import json
import secrets
import subprocess
import sys
import urllib.parse
import uuid
from pathlib import Path

sys.path.insert(0, str(Path(__file__).resolve().parent))

from checkout_smoke import (  # noqa: E402
    BFF, ENV, KC, REALM, admin_token, bff_json, bff_login, call,
)

ADMIN_BASE = f'{KC}/admin/realms/{REALM}'


def create_customer(admin):
    username = 'local-referral-smoke-' + uuid.uuid4().hex[:10]
    password = secrets.token_urlsafe(32)
    user = {
        'username': username,
        'email': username + '@example.com',
        'firstName': 'Local',
        'lastName': 'Referral',
        'enabled': True,
        'emailVerified': True,
        'requiredActions': [],
        'credentials': [{'type': 'password', 'value': password, 'temporary': False}],
    }
    status, _, _ = call(
        f'{ADMIN_BASE}/users', 'POST', json.dumps(user).encode(),
        {'Authorization': 'Bearer ' + admin, 'Content-Type': 'application/json'},
        cookie_header=False,
    )
    if status not in (201, 204):
        raise RuntimeError(f'create user HTTP {status}')
    status, _, raw = call(
        f'{ADMIN_BASE}/users?' + urllib.parse.urlencode({'username': username, 'exact': 'true'}),
        headers={'Authorization': 'Bearer ' + admin}, cookie_header=False,
    )
    uid = json.loads(raw)[0]['id']
    status, _, raw = call(
        f'{ADMIN_BASE}/roles/CUSTOMER', headers={'Authorization': 'Bearer ' + admin},
        cookie_header=False,
    )
    role = json.loads(raw)
    status, _, _ = call(
        f'{ADMIN_BASE}/users/{uid}/role-mappings/realm', 'POST',
        json.dumps([{'id': role['id'], 'name': role['name']}]).encode(),
        {'Authorization': 'Bearer ' + admin, 'Content-Type': 'application/json'},
        cookie_header=False,
    )
    if status not in (201, 204):
        raise RuntimeError(f'assign role HTTP {status}')
    return username, password, uid


def remove_customer(admin, uid):
    call(f'{ADMIN_BASE}/users/{uid}', 'DELETE',
         headers={'Authorization': 'Bearer ' + admin}, cookie_header=False)


def remove_commerce_rows(uid):
    """Remove the synthetic party and referral rows created by this smoke."""
    sql = (
        "DELETE FROM commerce.referral_attribution WHERE referrer_party_id IN "
        "(SELECT id FROM commerce.party WHERE identity_subject=:'subject') OR referred_party_id IN "
        "(SELECT id FROM commerce.party WHERE identity_subject=:'subject');"
        "DELETE FROM commerce.referral_code WHERE party_id IN "
        "(SELECT id FROM commerce.party WHERE identity_subject=:'subject');"
        "DELETE FROM commerce.customer_profile WHERE party_id IN "
        "(SELECT id FROM commerce.party WHERE identity_subject=:'subject');"
        "DELETE FROM commerce.party WHERE identity_subject=:'subject';"
    )
    # psql variables cannot be bound through -c, so interpolate the known UUID.
    if not all(c in '0123456789abcdef-' for c in uid.lower()):
        return
    try:
        subprocess.run(
            ['docker', 'exec', 'nigson-local-postgres-1', 'psql', '-U', 'nigson_bootstrap',
             '-d', 'nigson', '-v', 'ON_ERROR_STOP=1', '-c',
             sql.replace(":'subject'", f"'{uid}'")],
            check=True, capture_output=True, timeout=60,
        )
        print('synthetic commerce rows removed')
    except Exception as error:  # cleanup must not mask the verdict
        print('commerce cleanup skipped:', type(error).__name__)


def main():
    base = ENV.get('NIGSON_REWARD_REFERRAL_BASE_URL', '')
    if not base:
        raise RuntimeError('NIGSON_REWARD_REFERRAL_BASE_URL is not configured')
    print('referral link base:', urllib.parse.urlparse(base).netloc)

    admin = admin_token()
    username, password, uid = create_customer(admin)
    try:
        csrf = bff_login(username, password, '/account')
        print('PASS authenticated referral smoke session')

        path = '/api/v1/me/referral-code'
        status, body, raw = bff_json(path, csrf)
        if status != 404 or (body or {}).get('code') != 'NOT_FOUND':
            raise RuntimeError(f'GET before creation HTTP {status} {raw[:200]!r}')
        print('PASS GET before creation -> 404 NOT_FOUND (documented)')
        creation_key = str(uuid.uuid4())

        status, created, raw = bff_json(path, csrf, 'POST', {}, idempotency=creation_key)
        if status != 201:
            raise RuntimeError(f'POST create HTTP {status} {raw[:300]!r}')
        code = created.get('code', '')
        if not (16 <= len(code) <= 60) or not code.isalnum() or code != code.upper():
            raise RuntimeError('created code does not match the contract format')
        if not created.get('link', '').startswith(base.rstrip('/') + '/'):
            raise RuntimeError('created link does not start with the configured base')
        if created.get('qualifiedReferralCount') != 0:
            raise RuntimeError('new referral code should start with zero qualified referrals')
        print('PASS POST create -> 201', {'code': code, 'link': created['link']})

        status, fetched, raw = bff_json(path, csrf)
        if status != 200 or fetched != created:
            raise RuntimeError(f'GET after creation HTTP {status} {raw[:200]!r}')
        print('PASS GET after creation -> 200 with the same code and link')

        status, replayed_same_key, raw = bff_json(path, csrf, 'POST', {}, idempotency=creation_key)
        if status != 201 or replayed_same_key != created:
            raise RuntimeError(f'POST same-key replay HTTP {status} {raw[:200]!r}')
        print('PASS POST same-key replay -> 201 identical response')
        status, replayed, raw = bff_json(path, csrf, 'POST', {}, idempotency=str(uuid.uuid4()))
        if status != 201 or replayed != created:
            raise RuntimeError(f'POST new-key replay HTTP {status} {raw[:200]!r}')
        print('PASS POST new-key replay -> 201 stable code and link')
        return 0
    finally:
        remove_customer(admin, uid)
        print('temporary local identity removed')
        remove_commerce_rows(uid)


if __name__ == '__main__':
    sys.exit(main())
