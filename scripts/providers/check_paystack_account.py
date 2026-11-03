#!/usr/bin/env python3
"""Read-only Paystack TEST credential check. Never prints keys or account balances."""
from pathlib import Path
import json
import ssl
import stat
import urllib.request
import urllib.error

ROOT = Path(__file__).resolve().parents[2]


class NoRedirect(urllib.request.HTTPRedirectHandler):
    def redirect_request(self, req, fp, code, msg, headers, newurl):
        raise ValueError('Provider credential redirect refused')


def main():
    path = ROOT / '.local/provider-accounts.env'
    if stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise ValueError('Account credential file must be permission-restricted (chmod 600)')
    values = dict(line.split('=', 1) for line in path.read_text().splitlines() if line and not line.startswith('#'))
    key = values.get('PAYSTACK_TEST_SECRET_KEY', '')
    if not key.startswith('sk_test_'):
        raise ValueError('Only Paystack TEST credentials are permitted')
    try:
        import certifi
        tls = ssl.create_default_context(cafile=certifi.where())
    except ImportError:
        tls = ssl.create_default_context()
    opener = urllib.request.build_opener(NoRedirect(), urllib.request.HTTPSHandler(context=tls))
    req = urllib.request.Request('https://api.paystack.co/balance', headers={
        'Authorization': 'Bearer ' + key, 'Accept': 'application/json',
        'User-Agent': 'Nigson-provider-check/1.0'})
    try:
        with opener.open(req, timeout=15) as response:
            data = json.loads(response.read(65537))
            if response.status != 200 or data.get('status') is not True:
                raise ValueError('Provider authentication not confirmed')
        print('PASS: Paystack TEST credentials authenticated over verified TLS; no transaction created')
    except urllib.error.HTTPError as error:
        raise SystemExit('Paystack account check failed: HTTP ' + str(error.code)) from None
    except urllib.error.URLError:
        raise SystemExit('Paystack account check failed: TLS/network unavailable') from None


if __name__ == '__main__':
    main()
