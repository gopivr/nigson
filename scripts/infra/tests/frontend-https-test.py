#!/usr/bin/env python3
"""Isolated HTTPS acceptance using the real frontend image; no live providers."""
import argparse
import json
import ssl
import subprocess
import tempfile
import time
import urllib.error
import urllib.request
import uuid
from pathlib import Path

ROOT = Path(__file__).resolve().parents[3]


def run(*command, **kwargs):
    return subprocess.run(command, check=True, text=True, capture_output=True, **kwargs).stdout.strip()


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument('--image', required=True)
    args = parser.parse_args()
    name = 'nigson-test-mfe-' + uuid.uuid4().hex[:10]
    with tempfile.TemporaryDirectory(prefix='nigson-frontend-https-') as directory:
        temporary = Path(directory)
        temporary.chmod(0o755)
        run('openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-keyout', str(temporary / 'tls.key'),
            '-out', str(temporary / 'tls.crt'), '-days', '1', '-subj', '/CN=localhost',
            '-addext', 'subjectAltName=DNS:localhost,DNS:backend.synthetic.test')
        (temporary / 'tls.key').chmod(0o644)  # Disposable fixture; readable by non-root test container.
        backend = '''pid /tmp/nginx.pid;
events {}
http { client_body_temp_path /tmp/client_temp; proxy_temp_path /tmp/proxy_temp; fastcgi_temp_path /tmp/fastcgi_temp; uwsgi_temp_path /tmp/uwsgi_temp; scgi_temp_path /tmp/scgi_temp; server { listen 443 ssl;
ssl_certificate /run/secrets/tls.crt;
ssl_certificate_key /run/secrets/tls.key;
location = /api/v1/probe { default_type application/json; return 200 '{"cookie":"$http_cookie","csrf":"$http_x_csrf_token","query":"$args","method":"$request_method"}'; }
location = /api/v1/rejected { default_type application/problem+json; return 422 '{"code":"FULFILLMENT_RULE_VIOLATION"}'; }
location = /bff/session { add_header Cache-Control "no-store" always; add_header Set-Cookie "__Host-nigson-session=synthetic; Secure; HttpOnly; Path=/" always; return 401 '{"code":"AUTH_REQUIRED"}'; }
} }
'''
        (temporary / 'backend.conf').write_text(backend)
        config = (ROOT / 'infrastructure/terraform/shared/frontend/nginx.conf.tftpl').read_text()
        config = config.replace('${domain}', 'localhost').replace('${backend}', 'backend.synthetic.test')
        (temporary / 'frontend.conf').write_text(config)
        try:
            run('docker', 'network', 'create', name)
            common = ['--network', name, '--read-only', '--cap-drop', 'ALL', '--security-opt', 'no-new-privileges:true',
                      '--tmpfs', '/tmp', '--volume', str(temporary) + ':/run/secrets:ro']
            run('docker', 'run', '-d', '--name', name + '-backend', '--network-alias', 'backend.synthetic.test', *common,
                '--volume', str(temporary / 'backend.conf') + ':/etc/nginx/nginx.conf:ro', args.image)
            run('docker', 'run', '-d', '--name', name + '-frontend', *common,
                '--publish', '127.0.0.1::8443',
                '--volume', str(temporary / 'frontend.conf') + ':/etc/nginx/nginx.conf:ro',
                '--volume', str(temporary / 'tls.crt') + ':/etc/ssl/certs/ca-certificates.crt:ro', args.image)
            ports = json.loads(run('docker', 'inspect', name + '-frontend'))[0]['NetworkSettings']['Ports']['8443/tcp']
            base = 'https://localhost:' + ports[0]['HostPort']
            context = ssl.create_default_context(cafile=str(temporary / 'tls.crt'))
            def request(path, headers=None, data=None):
                req = urllib.request.Request(base + path, headers=headers or {}, data=data)
                try:
                    with urllib.request.urlopen(req, context=context, timeout=5) as response:
                        return response.status, response.headers, response.read()
                except urllib.error.HTTPError as error:
                    return error.code, error.headers, error.read()
            for attempt in range(30):
                try:
                    if request('/')[0] == 200:
                        break
                except (OSError, urllib.error.URLError):
                    pass
                if attempt == 29:
                    raise AssertionError('Frontend failed to start')
                time.sleep(0.5)
            status, headers, body = request('/admin/users')
            assert status == 200 and b'<html' in body and headers['Cache-Control'] == 'no-cache'
            assert headers['X-Content-Type-Options'] == 'nosniff'
            manifest = json.loads(request('/federation.manifest.json')[2])
            for name_remote, url in manifest.items():
                status, headers, body = request(url)
                assert status == 200 and 'json' in headers['Content-Type'], name_remote
                entry = json.loads(body)
                for record in entry.get('exposes', []) + entry.get('shared', []):
                    status, headers, _ = request(url.rsplit('/', 1)[0] + '/' + record['outFileName'])
                    assert status == 200 and 'javascript' in headers['Content-Type']
            for path in ['/remotes/missing/remoteEntry.json', '/assets/missing.png', '/missing.js']:
                assert request(path)[0] == 404, path
            assert request('/internal/v1/test')[0] == 403
            status, headers, body = request('/api/v1/probe?q=real', {'Cookie': 'session=synthetic', 'X-CSRF-Token': 'csrf-fixture'}, b'payload')
            assert status == 200 and json.loads(body) == {'cookie': 'session=synthetic', 'csrf': 'csrf-fixture', 'query': 'q=real', 'method': 'POST'}
            status, headers, body = request('/api/v1/rejected')
            assert status == 422 and json.loads(body)['code'] == 'FULFILLMENT_RULE_VIOLATION'
            status, headers, body = request('/bff/session')
            assert status == 401 and headers['Cache-Control'] == 'no-store' and '__Host-nigson-session=' in headers['Set-Cookie']
            print('PASS HTTPS shell/deep links, seven remotes and bundles, strict 404s, private paths, API cookie/CSRF/query/POST, 422/401 and Set-Cookie forwarding')
        except Exception:
            for suffix in ['-frontend', '-backend']:
                result = subprocess.run(['docker', 'logs', name + suffix], text=True, capture_output=True)
                print(result.stderr[-2000:])
            raise
        finally:
            for suffix in ['-frontend', '-backend']:
                subprocess.run(['docker', 'rm', '-f', name + suffix], capture_output=True)
            subprocess.run(['docker', 'network', 'rm', name], capture_output=True)


if __name__ == '__main__':
    main()
