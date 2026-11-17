#!/usr/bin/env python3
"""Local-only staging delivery-quote smoke.

Creates a temporary CUSTOMER in the local realm, completes the real BFF OIDC
flow, builds a cart, and requests PandaGo SANDBOX delivery quotes for a located
address and for a postal-only address (the storefront payload, resolved by
server-side Google geocoding). It also checks that a destination outside the
deliverable range returns 422 PROVIDER_RULE_VIOLATION rather than a retryable
outage. Secrets and tokens are never printed; the temporary identity and
addresses are removed. Requires `make run-core` plus the Angular shell on port
4200 for the BFF callback.
"""

import http.cookiejar
import json
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
ENV = dict(
    line.split('=', 1) for line in (ROOT / '.env').read_text().splitlines()
    if line and not line.startswith('#') and '=' in line
)
BFF = 'http://localhost:8081'
KC = 'http://localhost:8180'
REALM = 'nigson-local'
ORIGIN = 'http://localhost:4200'


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        return None


jar = http.cookiejar.CookieJar()


def call(url, method='GET', data=None, headers=None, cookie_header=True):
    merged = dict(headers or {})
    if cookie_header and jar:
        # Python's cookiejar refuses Secure cookies over http://localhost;
        # browsers treat localhost as trustworthy, so replay them manually.
        merged.setdefault('Cookie', '; '.join(f'{c.name}={c.value}' for c in jar))
    request = urllib.request.Request(url, data=data, headers=merged, method=method)
    opener = urllib.request.build_opener(
        urllib.request.ProxyHandler({}), NoRedirect(), urllib.request.HTTPCookieProcessor(jar)
    )
    try:
        with opener.open(request, timeout=60) as response:
            return response.status, dict(response.headers), response.read(200000)
    except urllib.error.HTTPError as error:
        return error.code, dict(error.headers), error.read(200000)


def admin_token():
    body = urllib.parse.urlencode({
        'grant_type': 'password',
        'client_id': 'admin-cli',
        'username': ENV['KEYCLOAK_BOOTSTRAP_ADMIN_USERNAME'],
        'password': ENV['KEYCLOAK_BOOTSTRAP_ADMIN_PASSWORD'],
    }).encode()
    status, _, raw = call(
        f'{KC}/realms/master/protocol/openid-connect/token', 'POST', body,
        {'Content-Type': 'application/x-www-form-urlencoded'}, cookie_header=False,
    )
    if status != 200:
        raise RuntimeError(f'bootstrap admin token HTTP {status}')
    return json.loads(raw)['access_token']


def bff_json(path, token, method='GET', body=None, version=None, idempotency=None):
    headers = {'Accept': 'application/json', 'Origin': ORIGIN}
    if body is not None or method == 'DELETE':
        headers.update({
            'Content-Type': 'application/json',
            'Idempotency-Key': idempotency or str(uuid.uuid4()),
            'X-CSRF-TOKEN': token,
        })
    if version is not None:
        headers['If-Match'] = '"' + str(version) + '"'
    status, _, raw = call(
        BFF + path, method, json.dumps(body).encode() if body is not None else None, headers,
    )
    try:
        parsed = json.loads(raw) if raw else None
    except ValueError:
        parsed = None
    return status, parsed, raw


def main():
    admin = admin_token()
    admin_base = f'{KC}/admin/realms/{REALM}'
    username = 'local-quote-smoke-' + uuid.uuid4().hex[:10]
    password = secrets.token_urlsafe(32)
    user = {
        'username': username,
        'email': username + '@example.invalid',
        'firstName': 'Local',
        'lastName': 'Quote',
        'enabled': True,
        'emailVerified': True,
        'requiredActions': [],
        'credentials': [{'type': 'password', 'value': password, 'temporary': False}],
    }
    created = False
    address_ids = []
    csrf = ''
    try:
        status, _, raw = call(
            f'{admin_base}/users', 'POST', json.dumps(user).encode(),
            {'Authorization': 'Bearer ' + admin, 'Content-Type': 'application/json'},
            cookie_header=False,
        )
        if status not in (201, 204):
            raise RuntimeError(f'create user HTTP {status}')
        created = True
        status, _, raw = call(
            f'{admin_base}/users?' + urllib.parse.urlencode({'username': username, 'exact': 'true'}),
            headers={'Authorization': 'Bearer ' + admin}, cookie_header=False,
        )
        uid = json.loads(raw)[0]['id']
        status, _, raw = call(
            f'{admin_base}/roles/CUSTOMER', headers={'Authorization': 'Bearer ' + admin},
            cookie_header=False,
        )
        role = json.loads(raw)
        status, _, _ = call(
            f'{admin_base}/users/{uid}/role-mappings/realm', 'POST',
            json.dumps([{'id': role['id'], 'name': role['name']}]).encode(),
            {'Authorization': 'Bearer ' + admin, 'Content-Type': 'application/json'},
            cookie_header=False,
        )
        if status not in (201, 204):
            raise RuntimeError(f'assign role HTTP {status}')

        # Real OIDC login through the BFF.
        status, headers, _ = call(BFF + '/bff/login?returnTo=/checkout', cookie_header=False)
        if status != 302:
            raise RuntimeError(f'/bff/login HTTP {status}')
        authorization = headers['Location']
        if not authorization.startswith(KC + f'/realms/{REALM}/protocol/openid-connect/auth'):
            raise RuntimeError('unexpected authorization endpoint')
        status, headers, html = call(authorization)
        if status != 200:
            raise RuntimeError(f'login form HTTP {status}')
        text = html.decode()
        action = None
        for fragment in text.split('<form')[1:]:
            if 'action=' in fragment:
                action = fragment.split('action="', 1)[1].split('"', 1)[0].replace('&amp;', '&')
                break
        if not action:
            raise RuntimeError('login form action missing')
        fields = []
        for chunk in text.split('<input')[1:]:
            if 'name="' not in chunk:
                continue
            name = chunk.split('name="', 1)[1].split('"', 1)[0]
            value = ''
            if 'value="' in chunk:
                value = chunk.split('value="', 1)[1].split('"', 1)[0]
            fields.append((name, value.replace('&amp;', '&')))
        form = [(n, v) for n, v in fields if n not in ('username', 'password')]
        form += [('username', username), ('password', password)]
        status, headers, _ = call(
            action, 'POST', urllib.parse.urlencode(form).encode(),
            {'Content-Type': 'application/x-www-form-urlencoded'},
        )
        if status != 302 or '/bff/callback' not in headers.get('Location', ''):
            raise RuntimeError(f'credential submit HTTP {status}')
        callback = headers['Location']
        if callback.startswith('/'):
            callback = BFF + callback
        status, headers, _ = call(callback)
        if status != 302:
            raise RuntimeError(f'/bff/callback HTTP {status}')
        status, _, raw = call(BFF + '/bff/session')
        session = json.loads(raw)
        if not session.get('authenticated'):
            raise RuntimeError('BFF session not authenticated')
        csrf = session['csrfToken']
        print('PASS authenticated staging quote smoke session')

        # Real catalogue + cart.
        status, markets, _ = bff_json('/api/v1/markets', csrf)
        market = next(m for m in markets if m['code'] == 'NG')
        status, products, _ = bff_json('/api/v1/catalogue/products?market=NG&size=1', csrf)
        items = products.get('items', [])
        if not items:
            raise RuntimeError('no catalogue products')
        status, product, _ = bff_json('/api/v1/catalogue/products/' + items[0]['id'] + '?market=NG', csrf)
        sku = product['skus'][0]['id']
        status, cart, raw = bff_json('/api/v1/carts', csrf, 'POST', {'marketId': market['id']})
        if status != 201:
            raise RuntimeError(f'create cart HTTP {status} {raw[:200]!r}')
        status, cart, raw = bff_json(
            f"/api/v1/carts/{cart['id']}/lines/{sku}", csrf, 'PUT', {'quantity': '1'},
            version=cart['version'],
        )
        if status != 200:
            raise RuntimeError(f'add cart line HTTP {status} {raw[:200]!r}')

        outlet = json.loads((ROOT / '.local/pandago/outlet.json').read_text())
        base_address = {
            'label': 'Staging quote smoke',
            'recipient': 'Staging Smoke',
            'phone': '+2348012345678',
            'line1': '12 Adeola Odeku Street',
            'line2': None,
            'city': 'Lagos',
            'region': 'Lagos',
            'postalCode': '101241',
            'countryCode': 'NG',
            'organizationId': None,
            'isDefault': False,
        }
        located = dict(base_address, latitude=round(float(outlet['latitude']), 6),
                       longitude=round(float(outlet['longitude']), 6))
        status, address, raw = bff_json('/api/v1/me/addresses', csrf, 'POST', located)
        if status != 201:
            raise RuntimeError(f'create located address HTTP {status} {raw[:200]!r}')
        address_ids.append((address['id'], address['version']))

        # PandaGo staging intermittently answers 503 (notably right after a restart);
        # a bounded retry is safe because a quote has no booking side effect.
        def delivery_quote(address_id, attempts=3):
            for attempt in range(1, attempts + 1):
                status, body, raw = bff_json(
                    f"/api/v1/carts/{cart['id']}/delivery-quotes", csrf, 'POST',
                    {'addressId': address_id}, version=cart['version'],
                )
                if status == 201 or status < 500 or attempt == attempts:
                    return status, body, raw
                print(f'delivery quote attempt {attempt} -> {status}; retrying')

        status, quote, raw = delivery_quote(address['id'])
        print('located address delivery quote ->', status,
              {k: (quote or {}).get(k) for k in ('provider', 'mode', 'currency', 'deliveryAmount')})
        if status != 201 or quote is None or quote.get('provider') != 'PANDAGO':
            raise RuntimeError('staging delivery quote did not succeed')

        status, plain, raw = bff_json('/api/v1/me/addresses', csrf, 'POST', base_address)
        if status != 201:
            raise RuntimeError(f'create postal address HTTP {status} {raw[:200]!r}')
        address_ids.append((plain['id'], plain['version']))
        status, _, raw = delivery_quote(plain['id'])
        problem = json.loads(raw) if raw else {}
        print('postal-only address delivery quote ->', status, problem.get('code'))
        if status != 201:
            raise RuntimeError(f'postal-only delivery quote HTTP {status} {raw[:300]!r}')

        # PandaGo refuses destinations outside its deliverable range with a 422.
        # That is a rule outcome for the customer address, not a retryable outage.
        ibadan = dict(base_address, label='Out of range smoke', line1='34 Alayande Close',
                      city='Ibadan', region='Oyo', postalCode='200212')
        status, outside, raw = bff_json('/api/v1/me/addresses', csrf, 'POST', ibadan)
        if status != 201:
            raise RuntimeError(f'create out-of-range address HTTP {status} {raw[:200]!r}')
        address_ids.append((outside['id'], outside['version']))
        status, _, raw = delivery_quote(outside['id'])
        problem = json.loads(raw) if raw else {}
        print('out-of-range delivery quote ->', status, problem.get('code'))
        if status != 422 or problem.get('code') != 'PROVIDER_RULE_VIOLATION':
            raise RuntimeError(f'out-of-range delivery quote was not a provider rule violation: {status} {raw[:300]!r}')
        return 0
    finally:
        if created:
            for address_id, version in address_ids:
                try:
                    bff_json(f'/api/v1/me/addresses/{address_id}', csrf, 'DELETE', version=version)
                except Exception:
                    pass
            status, _, raw = call(
                f'{admin_base}/users?' + urllib.parse.urlencode({'username': username, 'exact': 'true'}),
                headers={'Authorization': 'Bearer ' + admin}, cookie_header=False,
            )
            if status == 200:
                for row in json.loads(raw or b'[]'):
                    call(f"{admin_base}/users/{row['id']}", 'DELETE',
                         headers={'Authorization': 'Bearer ' + admin}, cookie_header=False)
                print('temporary local identity removed')


if __name__ == '__main__':
    sys.exit(main())
