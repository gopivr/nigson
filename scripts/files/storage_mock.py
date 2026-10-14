"""Isolated stateful S3 protocol fixture. TLS/SigV4; never used by production."""
import base64
from contextlib import contextmanager
from datetime import datetime, timezone
import hashlib
import hmac
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
from pathlib import Path
import ssl
import subprocess
import threading
from urllib.parse import parse_qsl, quote, unquote, urlsplit
import uuid

ROOT = Path(__file__).resolve().parents[2]
KEY = 'mock-local-s3'
SECRET = 'mock-local-s3-secret'
REGION = 'us-east-1'


@contextmanager
def fixture(environment):
    base = ROOT / '.build/files001' / uuid.uuid4().hex
    base.mkdir(parents=True, exist_ok=True)
    cert, key = base / 'ca.pem', base / 'key.pem'
    subprocess.run(['openssl', 'req', '-x509', '-newkey', 'rsa:2048', '-nodes', '-days', '2', '-subj', '/CN=localhost', '-addext', 'subjectAltName=DNS:localhost,IP:127.0.0.1', '-keyout', str(key), '-out', str(cert)], check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    key.chmod(0o600)
    buckets = ['synthetic-quarantine', 'synthetic-public', 'synthetic-private']
    state = {'objects': {}, 'copies': 0, 'heads': 0, 'fault': None, 'signature_denials': 0}
    lock = threading.Lock()
    admin = uuid.uuid4().hex

    class Handler(BaseHTTPRequestHandler):
        protocol_version = 'HTTP/1.1'

        def log_message(self, *_):
            pass  # URLs/headers contain temporary capabilities.

        def reply(self, status, body=b'', headers=None):
            self.send_response(status)
            for name, value in (headers or {}).items():
                self.send_header(name, value)
            if not headers or 'Content-Length' not in headers:
                self.send_header('Content-Length', str(len(body)))
            self.end_headers()
            if self.command != 'HEAD':
                self.wfile.write(body)

        def signature(self, payload):
            url = urlsplit(self.path)
            query = dict(parse_qsl(url.query, keep_blank_values=True))
            presigned = 'X-Amz-Signature' in query
            if presigned:
                credential = query.get('X-Amz-Credential', '')
                signed = query.get('X-Amz-SignedHeaders', '')
                signature = query.pop('X-Amz-Signature')
                timestamp = query.get('X-Amz-Date', '')
                if query.get('X-Amz-Algorithm') != 'AWS4-HMAC-SHA256':
                    return False
                try:
                    age = (datetime.now(timezone.utc) - datetime.strptime(timestamp, '%Y%m%dT%H%M%SZ').replace(tzinfo=timezone.utc)).total_seconds()
                    if age < -5 or age > int(query.get('X-Amz-Expires', '0')) or int(query['X-Amz-Expires']) > 300:
                        return False
                except (ValueError, KeyError):
                    return False
                payload_hash = 'UNSIGNED-PAYLOAD'
            else:
                auth = self.headers.get('Authorization', '')
                if not auth.startswith('AWS4-HMAC-SHA256 '):
                    return False
                parts = dict(part.strip().split('=', 1) for part in auth[17:].split(','))
                credential, signed, signature = (parts.get(p, '') for p in ('Credential', 'SignedHeaders', 'Signature'))
                timestamp = self.headers.get('x-amz-date', '')
                payload_hash = self.headers.get('x-amz-content-sha256', hashlib.sha256(payload).hexdigest())
                if payload_hash not in ('UNSIGNED-PAYLOAD', hashlib.sha256(payload).hexdigest()):
                    return False
            parts = credential.split('/')
            if len(parts) != 5 or parts[0] != KEY or parts[2:] != [REGION, 's3', 'aws4_request'] or parts[1] != timestamp[:8]:
                return False
            names = signed.split(';')
            if names != sorted(set(names)) or 'host' not in names:
                return False
            canonical_headers = ''.join(name + ':' + ' '.join(self.headers.get(name, '').strip().split()) + '\n' for name in names)
            q = '&'.join(quote(k, safe='-_.~') + '=' + quote(v, safe='-_.~') for k, v in sorted(query.items()))
            uri = quote(unquote(url.path), safe='/-_.~')
            canonical = '\n'.join([self.command, uri, q, canonical_headers, signed, payload_hash])
            scope = '/'.join(parts[1:])
            string = '\n'.join(['AWS4-HMAC-SHA256', timestamp, scope, hashlib.sha256(canonical.encode()).hexdigest()])
            signing = ('AWS4' + SECRET).encode()
            for part in parts[1:]:
                signing = hmac.new(signing, part.encode(), hashlib.sha256).digest()
            return hmac.compare_digest(hmac.new(signing, string.encode(), hashlib.sha256).hexdigest(), signature)

        def do_GET(self):
            self.handle_request()

        def do_HEAD(self):
            self.handle_request()

        def do_PUT(self):
            self.handle_request()

        def do_POST(self):
            self.handle_request()

        def handle_request(self):
            try:
                length = int(self.headers.get('Content-Length', '0'))
                if length < 0 or length > 10 * 1024 * 1024:
                    self.reply(413)
                    return
                payload = self.rfile.read(length)
                if self.path == '/__test' and hmac.compare_digest(self.headers.get('X-Test-Control', ''), admin):
                    with lock:
                        if self.command == 'POST':
                            control = json.loads(payload)
                            if control.get('fault') not in ('BEFORE_ACCEPT', 'AFTER_ACCEPT', 'INVALID_HEAD_CHECKSUM', None):
                                raise ValueError('control')
                            state['fault'] = control.get('fault')
                        response = json.dumps({k: v for k, v in state.items() if k != 'objects'}).encode()
                    self.reply(200, response, {'Content-Type': 'application/json'})
                    return
                if not self.signature(payload):
                    with lock:
                        state['signature_denials'] += 1
                    self.reply(403)
                    return
                path = unquote(urlsplit(self.path).path).strip('/').split('/', 1)
                if len(path) != 2 or path[0] not in buckets:
                    self.reply(404)
                    return
                bucket, object_key = path
                with lock:
                    versions = state['objects'].get((bucket, object_key), [])
                    if self.command in ('GET', 'HEAD'):
                        if self.command == 'HEAD':
                            state['heads'] += 1
                        if not versions:
                            self.reply(404)
                            return
                        obj = versions[-1]
                        headers = {'Content-Type': obj['mime'], 'Content-Length': str(len(obj['bytes'])), 'ETag': obj['etag'], 'x-amz-version-id': obj['version'], 'x-amz-checksum-sha256': obj['checksum'], 'x-amz-server-side-encryption': 'AES256', 'x-amz-meta-owner': obj['owner'], 'x-amz-meta-asset': obj['asset']}
                        if self.command == 'HEAD' and state['fault'] == 'INVALID_HEAD_CHECKSUM':
                            state['fault'] = None
                            headers['x-amz-checksum-sha256'] = 'invalid-checksum'
                        self.reply(200, obj['bytes'], headers)
                        return
                    if self.command != 'PUT' or self.headers.get('If-None-Match') != '*':
                        self.reply(400)
                        return
                    if versions:
                        self.reply(412)
                        return
                    source = self.headers.get('x-amz-copy-source')
                    if source:
                        state['copies'] += 1
                        fault = state['fault']
                        state['fault'] = None
                        if fault == 'BEFORE_ACCEPT':
                            self.close_connection = True
                            return
                        source_url = urlsplit(unquote(source))
                        source_bucket, source_key = source_url.path.strip('/').split('/', 1)
                        version = dict(parse_qsl(source_url.query)).get('versionId')
                        sources = state['objects'].get((source_bucket, source_key), [])
                        found = next((v for v in sources if v['version'] == version), None)
                        if found is None or found['etag'] != self.headers.get('x-amz-copy-source-if-match'):
                            self.reply(412)
                            return
                        obj = {**found, 'version': str(uuid.uuid4())}
                        state['objects'][(bucket, object_key)] = [obj]
                        if fault == 'AFTER_ACCEPT':
                            self.close_connection = True
                            return
                        body = ('<CopyObjectResult><ETag>' + obj['etag'] + '</ETag><LastModified>2026-10-10T00:00:00Z</LastModified><ChecksumSHA256>' + obj['checksum'] + '</ChecksumSHA256></CopyObjectResult>').encode()
                        self.reply(200, body, {'Content-Type': 'application/xml', 'x-amz-version-id': obj['version']})
                    else:
                        checksum = base64.b64encode(hashlib.sha256(payload).digest()).decode()
                        if self.headers.get('x-amz-checksum-sha256') != checksum or self.headers.get('x-amz-server-side-encryption') != 'AES256':
                            self.reply(400)
                            return
                        obj = {'bytes': payload, 'checksum': checksum, 'mime': self.headers.get('Content-Type'), 'owner': self.headers.get('x-amz-meta-owner'), 'asset': self.headers.get('x-amz-meta-asset'), 'etag': '"' + hashlib.md5(payload, usedforsecurity=False).hexdigest() + '"', 'version': str(uuid.uuid4())}
                        state['objects'][(bucket, object_key)] = [obj]
                        self.reply(200, headers={'ETag': obj['etag'], 'x-amz-version-id': obj['version'], 'x-amz-checksum-sha256': checksum})
            except (ValueError, KeyError, OSError):
                self.close_connection = True

    server = ThreadingHTTPServer(('127.0.0.1', 0), Handler)
    server.daemon_threads = True
    context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER)
    context.minimum_version = ssl.TLSVersion.TLSv1_2
    context.load_cert_chain(cert, key)
    server.socket = context.wrap_socket(server.socket, server_side=True)
    thread = threading.Thread(target=server.serve_forever, daemon=True)
    thread.start()
    endpoint = 'https://localhost:' + str(server.server_port)
    data = {'endpoint': endpoint, 'ca': str(cert), 'admin': admin, 'region': REGION, 'buckets': buckets}
    config = base / 'fixture.json'
    config.write_text(json.dumps(data))
    config.chmod(0o600)
    try:
        yield {'NIGSON_TEST_STORAGE_FIXTURE': str(config)}
    finally:
        server.shutdown()
        server.server_close()
        thread.join(5)
        (base / 'safe-result.json').write_text(json.dumps({k: v for k, v in state.items() if k != 'objects'}))
