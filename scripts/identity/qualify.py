"""Isolated optimized Keycloak qualification. No mutations to nigson-local resources."""
from __future__ import annotations
import base64
import hashlib
import html.parser
import http.cookiejar
import json
import os
from pathlib import Path
import secrets
import socket
import ssl
import subprocess
import sys
import time
import urllib.error
import urllib.parse
import urllib.request
from datetime import datetime, timedelta, timezone
from cryptography import x509
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from cryptography.x509.oid import NameOID
import psycopg
from admin import Admin
from database import bootstrap
from realm_policy import realm_policy
from backup import encrypt, decrypt

ROOT = Path(__file__).resolve().parents[2]
PG = 'postgres:18.6@sha256:5a5a84b19854a9ffaa54082c166ff4ec27473a361e496e5ea167f298f2da9722'
PROXY = 'nginx:1.30.5-alpine@sha256:0985e772fb9f729e6fa0980da05fca5d9c468e870eed43071545afa9d2e27d94'


def command(*args, env=None, data=None):
    result = subprocess.run(args, input=data, capture_output=True, env={**os.environ, **(env or {})}, timeout=180)
    if result.returncode:
        private(ROOT / '.build/idp001/last-command-error.log', result.stderr + result.stdout)
        raise RuntimeError('QUALIFICATION_COMMAND_FAILED:' + args[0])
    return result.stdout


def docker(*args, **kwargs):
    return command('docker', *args, **kwargs)


def port():
    with socket.socket() as sock:
        sock.bind(('127.0.0.1', 0))
        return sock.getsockname()[1]


def private(path, data):
    path.write_bytes(data if isinstance(data, (bytes, bytearray)) else data.encode())
    path.chmod(0o600)


def snapshot():
    rows = [json.loads(line) for line in docker('ps', '-a', '--filter', 'label=com.docker.compose.project=nigson-local', '--format', 'json').splitlines()]
    result = []
    for row in rows:
        item = json.loads(docker('inspect', row['ID']))[0]
        result.append({'id': item['Id'], 'name': item['Name'], 'started': item['State']['StartedAt'], 'running': item['State']['Running'], 'mounts': sorted([{k: m[k] for k in ('Type', 'Destination', 'Source')} for m in item['Mounts']],key=lambda m:m['Destination'])})
    return sorted(result, key=lambda x: x['name'])


class Form(html.parser.HTMLParser):
    def __init__(self, body):
        super().__init__(); self.action = None; self.inputs = {}; self.text = body
        self.feed(body)
    def handle_starttag(self, tag, attrs):
        fields = dict(attrs)
        if tag == 'form' and not self.action:
            self.action = fields.get('action')
        if tag == 'input' and fields.get('name') and fields.get('type') != 'submit':
            self.inputs[fields['name']] = fields.get('value', '')


class Browser:
    def __init__(self, ca, callback):
        self.callback = callback; self.location = None
        class StopCallback(urllib.request.HTTPRedirectHandler):
            def redirect_request(inner, req, fp, code, msg, headers, newurl):
                if newurl.startswith(callback):
                    self.location = newurl
                    return None
                return super().redirect_request(req, fp, code, msg, headers, newurl)
        self.http = urllib.request.build_opener(urllib.request.ProxyHandler({}), urllib.request.HTTPCookieProcessor(http.cookiejar.CookieJar()), urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=ca)), StopCallback())
    def request(self, url, data=None):
        try:
            with self.http.open(urllib.request.Request(url, data=None if data is None else urllib.parse.urlencode(data).encode()), timeout=15) as response:
                return Form(response.read(524288).decode())
        except urllib.error.HTTPError as failure:
            if failure.code == 302 and self.location:
                return Form('')
            raise RuntimeError('BROWSER_HTTP_REJECTED_' + str(failure.code)) from None


class Qualification:
    def __init__(self):
        if os.environ.get('ENV') != 'test':
            raise ValueError('ISOLATED_TEST_ENV_REQUIRED')
        context = json.loads(docker('context', 'inspect'))[0]
        if not context['Endpoints']['docker']['Host'].startswith('unix://'):
            raise ValueError('LOCAL_DOCKER_REQUIRED')
        self.before = snapshot()
        if len(self.before) != 5 or not all(x['running'] for x in self.before):
            raise ValueError('ORIGINAL_LOCAL_STATE_MUST_BE_RUNNING')
        self.name = 'nigson-test-idp-' + secrets.token_hex(5)
        self.work = ROOT / '.build/idp001' / self.name; self.work.mkdir(parents=True); self.work.chmod(0o700)
        self.containers = []; self.public, self.admin_port, self.dbport = port(), port(), port()
        self.origin = 'https://localhost:' + str(self.public)
        self.admin_origin = 'https://127.0.0.1:' + str(self.admin_port)
        self.realm = 'nigson-idp-qualification'
        self.callback = self.origin + '/bff/callback'
        self.password = 'test-' + secrets.token_urlsafe(32)
        self.secrets = {x: 'test-' + secrets.token_urlsafe(32) for x in ('nigson-bff','nigson-commerce','nigson-experience','nigson-integration')}
        self.checks = []; self.management = {}
    def passed(self, name):
        self.checks.append(name); print('PASS ' + name, flush=True)
    def certificates(self):
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        name = x509.Name([x509.NameAttribute(NameOID.COMMON_NAME, 'Isolated identity qualification CA')])
        now = datetime.now(timezone.utc)
        ca = x509.CertificateBuilder().subject_name(name).issuer_name(name).public_key(key.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-timedelta(minutes=1)).not_valid_after(now+timedelta(days=2)).add_extension(x509.BasicConstraints(ca=True,path_length=0),critical=True).sign(key,hashes.SHA256())
        serverkey = rsa.generate_private_key(public_exponent=65537,key_size=2048)
        import ipaddress
        cert = x509.CertificateBuilder().subject_name(x509.Name([x509.NameAttribute(NameOID.COMMON_NAME,'localhost')])).issuer_name(name).public_key(serverkey.public_key()).serial_number(x509.random_serial_number()).not_valid_before(now-timedelta(minutes=1)).not_valid_after(now+timedelta(days=2)).add_extension(x509.SubjectAlternativeName([x509.DNSName(x) for x in ('localhost','pg','node1','node2','proxy','restored')]+[x509.IPAddress(ipaddress.ip_address('127.0.0.1'))]),critical=False).sign(key,hashes.SHA256())
        private(self.work/'ca.crt',ca.public_bytes(serialization.Encoding.PEM))
        private(self.work/'server.crt',cert.public_bytes(serialization.Encoding.PEM))
        private(self.work/'server.key',serverkey.private_bytes(serialization.Encoding.PEM,serialization.PrivateFormat.PKCS8,serialization.NoEncryption()))
        self.ca = str(self.work/'ca.crt')
    def run(self, short, image, args, env=None, extra=None):
        name = self.name + '-' + short
        flags = []
        for key in env or {}:
            flags += ['-e', key]
        self.containers.append(name)
        docker('run','-d','--name',name,'--network',self.name,'--network-alias',short,*flags,*(extra or []),image,*args,env=env)
        return name
    def db_ready(self, hostport):
        dsn = psycopg.conninfo.make_conninfo(host='localhost',port=hostport,dbname='postgres',user='postgres',password=self.password,sslmode='verify-full',sslrootcert=self.ca)
        for _ in range(60):
            try:
                with psycopg.connect(dsn,connect_timeout=2): pass
                return dsn
            except psycopg.OperationalError: time.sleep(1)
        raise RuntimeError('IDENTITY_DATABASE_STARTUP_TIMEOUT')
    def keycloak_env(self, pg='pg'):
        return {'APP_ENV':'test','KC_HOSTNAME':self.origin,'KC_HOSTNAME_ADMIN':self.admin_origin,'KC_DB_URL':f'jdbc:postgresql://{pg}:5432/keycloak','KC_DB_USERNAME':'nigson_identity','KC_DB_PASSWORD':self.password,'KC_PROXY_TRUSTED_ADDRESSES':self.network_cidr,'NIGSON_IDENTITY_POLICY_REVISION':'IDP-001-offline-qualification','NIGSON_IDENTITY_CERT_PEM':(self.work/'server.crt').read_text(),'NIGSON_IDENTITY_KEY_PEM':(self.work/'server.key').read_text(),'NIGSON_IDENTITY_DB_CA_PEM':(self.work/'ca.crt').read_text()}
    def node(self, short, pg='pg'):
        # Docker initializes the image-owned data volume; tmp is writable, root stays read-only.
        management_port=port();self.management[short]=management_port
        return self.run(short,'nigson/keycloak:qualification',['start','--optimized'],self.keycloak_env(pg),['-p',f'127.0.0.1:{management_port}:9000','--read-only','--tmpfs','/tmp:rw,nosuid,noexec,size=128m','--cap-drop','ALL'])
    def wait_http(self, url):
        http = urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=self.ca)))
        for _ in range(90):
            try:
                with http.open(url,timeout=2) as response:
                    if response.status == 200: return
            except Exception: time.sleep(1)
        raise RuntimeError('IDENTITY_HTTPS_STARTUP_TIMEOUT')
    def proxy(self):
        config = f'''events {{}} http {{ proxy_connect_timeout 2s; proxy_read_timeout 5s; resolver 127.0.0.11 valid=1s; access_log off; error_log /dev/stderr crit;
upstream identity {{ zone identity 64k; server node1:8443 resolve max_fails=1 fail_timeout=2s; server node2:8443 resolve max_fails=1 fail_timeout=2s; }}
server {{ listen 8443 ssl; ssl_certificate /cert/server.crt; ssl_certificate_key /cert/server.key;
location ~ ^/(realms/{self.realm}/|resources/|\\.well-known/) {{ proxy_pass https://identity; proxy_ssl_trusted_certificate /cert/ca.crt; proxy_ssl_verify on; proxy_ssl_server_name on; proxy_ssl_name node1; proxy_set_header Host localhost:{self.public}; proxy_set_header X-Forwarded-Host localhost:{self.public}; proxy_set_header X-Forwarded-Proto https; proxy_set_header X-Forwarded-Port {self.public}; }}
location / {{ return 403; }} }}
server {{ listen 9443 ssl; ssl_certificate /cert/server.crt; ssl_certificate_key /cert/server.key;
location / {{ proxy_pass https://identity; proxy_ssl_trusted_certificate /cert/ca.crt; proxy_ssl_verify on; proxy_ssl_server_name on; proxy_ssl_name node1; proxy_set_header Host 127.0.0.1:{self.admin_port}; proxy_set_header X-Forwarded-Host 127.0.0.1:{self.admin_port}; proxy_set_header X-Forwarded-Proto https; proxy_set_header X-Forwarded-Port {self.admin_port}; }} }} }}'''
        private(self.work/'nginx.conf',config)
        self.run('proxy',PROXY,[],extra=['-p',f'127.0.0.1:{self.public}:8443','-p',f'127.0.0.1:{self.admin_port}:9443','-v',f'{self.work}:/cert:ro','-v',f'{self.work}/nginx.conf:/etc/nginx/nginx.conf:ro'])
    def browser_login(self, username, staff=False, invalid_pkce=False, code_replay=False, emit_code=False):
        verifier = secrets.token_urlsafe(32)
        query = {'client_id':'nigson-bff','redirect_uri':self.callback,'response_type':'code','scope':'openid','state':secrets.token_urlsafe(32),'nonce':secrets.token_urlsafe(32),'code_challenge':base64.urlsafe_b64encode(hashlib.sha256(verifier.encode()).digest()).decode().rstrip('='),'code_challenge_method':'S256'}
        browser = Browser(self.ca,self.callback)
        form = browser.request(self.origin+'/realms/'+self.realm+'/protocol/openid-connect/auth?'+urllib.parse.urlencode(query))
        assert form.action and not browser.location
        form = browser.request(form.action,{**form.inputs,'username':username,'password':self.password})
        if staff:
            assert not browser.location and ('totp' in form.inputs or 'otp' in form.inputs), 'STAFF_PASSWORD_ONLY_BYPASS'
            # Enrollment is required even for a staff principal without an existing OTP credential.
            import re, hmac, struct
            match = re.search(r'name="totpSecret"\s+value="([^"]+)"',form.text)
            if not match: match = re.search(r'value="([^"]+)"\s+name="totpSecret"',form.text)
            if match:
                self.staff_secret = match[1]
            else:
                assert getattr(self, 'staff_secret', None), 'TOTP_SECRET_MISSING'
            otpkey = self.staff_secret.encode()
            if getattr(self, 'staff_counter', None) == int(time.time()) // 30:
                time.sleep(31 - (time.time() % 30))
            self.staff_counter = int(time.time()) // 30
            digest = hmac.new(otpkey,struct.pack('>Q',int(time.time())//30),hashlib.sha1).digest(); offset=digest[-1]&15
            otp = str((int.from_bytes(digest[offset:offset+4],'big')&0x7fffffff)%1000000).zfill(6)
            field = 'totp' if 'totpSecret' in form.inputs else 'otp'
            form = browser.request(form.action,{**form.inputs,field:otp,'userLabel':'offline-qualification'})
        assert browser.location, 'AUTHORIZATION_CODE_MISSING'
        params = urllib.parse.parse_qs(urllib.parse.urlsplit(browser.location).query)
        assert params['state'][0] == query['state'] and 'code' in params
        exchange={'grant_type':'authorization_code','client_id':'nigson-bff','client_secret':self.secrets['nigson-bff'],'redirect_uri':self.callback,'code':params['code'][0],'code_verifier':verifier}
        if emit_code:
            return {'code': params['code'][0], 'state': query['state'], 'nonce': query['nonce'], 'verifier': verifier}
        if invalid_pkce:
            exchange.pop('code_verifier')
            try: self.token(exchange);raise AssertionError('PKCE_VERIFIER_BYPASS')
            except ValueError as failure: assert str(failure)=='ADMIN_HTTP_REJECTED_400'
            return None
        tokens=self.token(exchange)
        if code_replay:
            try: self.token(exchange);raise AssertionError('AUTHORIZATION_CODE_REPLAY_ACCEPTED')
            except ValueError as failure: assert str(failure)=='ADMIN_HTTP_REJECTED_400'
            return None
        encoded=tokens['id_token'].split('.')[1];claims=json.loads(base64.urlsafe_b64decode(encoded+'='*(-len(encoded)%4)))
        assert claims['nonce']==query['nonce'],'PROVIDER_NONCE_MISMATCH'
        return tokens
    def token(self, data):
        return self.admin.request('/realms/'+self.realm+'/protocol/openid-connect/token','POST',data,form=True)
    def users(self):
        prefix = '/admin/realms/'+self.realm
        for username in ('offline-retail','offline-staff'):
            self.admin.request(prefix+'/users','POST',{'username':username,'enabled':True,'firstName':'Offline','lastName':'Qualification','email':username+'@identity.test','emailVerified':True,'credentials':[{'type':'password','value':self.password,'temporary':False}]})
        uid=self.admin.request(prefix+'/users?username=offline-staff')[0]['id']
        role=self.admin.request(prefix+'/roles/nigson-staff-identity')
        self.admin.request(prefix+'/users/'+uid+'/role-mappings/realm','POST',[role])
    @staticmethod
    def otp(secret):
        import hmac, struct
        digest=hmac.new(secret.encode(),struct.pack('>Q',int(time.time())//30),hashlib.sha1).digest();offset=digest[-1]&15
        return str((int.from_bytes(digest[offset:offset+4],'big')&0x7fffffff)%1000000).zfill(6)
    def permanent_admin(self):
        from retire_bootstrap import retire
        self.admin_otp=secrets.token_urlsafe(15)
        prefix='/admin/realms/master'
        self.admin.request(prefix+'/users','POST',{'username':'offline-permanent','enabled':True,'firstName':'Offline','lastName':'Qualification','email':'offline-permanent@identity.test','emailVerified':True,'credentials':[{'type':'password','value':self.password,'temporary':False},{'type':'otp','secretData':json.dumps({'value':self.admin_otp}),'credentialData':json.dumps({'subType':'totp','digits':6,'period':30,'algorithm':'HmacSHA1','counter':0})}]})
        user=self.admin.request(prefix+'/users?username=offline-permanent')[0]
        self.admin.request(prefix+'/users/'+user['id']+'/role-mappings/realm','POST',[self.admin.request(prefix+'/roles/admin')])
        operator=Admin(self.admin_origin,self.ca)
        try: operator.login('offline-permanent',self.password);raise AssertionError('PERMANENT_ADMIN_MFA_BYPASS')
        except ValueError as failure: assert str(failure) in {'ADMIN_HTTP_REJECTED_401','ADMIN_HTTP_REJECTED_400'}
        operator.login('offline-permanent',self.password,self.otp(self.admin_otp))
        retire(operator,'offline-permanent','offline-bootstrap')
        try: self.admin.login('offline-bootstrap',self.password);raise AssertionError('BOOTSTRAP_NOT_RETIRED')
        except ValueError as failure: assert str(failure) in {'ADMIN_HTTP_REJECTED_401','ADMIN_HTTP_REJECTED_400'}
        self.admin=operator;self.passed('TEST-KEYCLOAK-BOOTSTRAP-RETIREMENT')

    def execute(self):
        self.certificates(); docker('network','create',self.name)
        self.network_cidr=json.loads(docker('network','inspect',self.name))[0]['IPAM']['Config'][0]['Subnet']
        # Override image entrypoint without leaking credentials on the process command line.
        script='cp /cert/server.key /tmp/server.key; cp /cert/server.crt /tmp/server.crt; chown postgres:postgres /tmp/server.key /tmp/server.crt; chmod 600 /tmp/server.key; exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tmp/server.crt -c ssl_key_file=/tmp/server.key -c log_min_error_statement=panic'
        self.pg=self.run('pg',PG,['-ec',script],{'POSTGRES_PASSWORD':self.password},['--entrypoint','bash','-p',f'127.0.0.1:{self.dbport}:5432','-v',f'{self.work}:/cert:ro'])
        self.dsn=self.db_ready(self.dbport); bootstrap(self.dsn,self.password,'test')
        with psycopg.connect(self.dsn,autocommit=True) as conn:
            conn.execute("CREATE ROLE commerce_runtime LOGIN PASSWORD 'test-isolation-only'")
        try:
            with psycopg.connect(**{**psycopg.conninfo.conninfo_to_dict(self.dsn),'dbname':'keycloak','user':'commerce_runtime','password':'test-isolation-only'}): pass
            raise AssertionError('BUSINESS_ROLE_IDENTITY_ACCESS')
        except psycopg.OperationalError: pass
        self.passed('TEST-KEYCLOAK-DB-ISOLATION')
        env={**self.keycloak_env(),'KC_BOOTSTRAP_ADMIN_USERNAME':'offline-bootstrap','KC_BOOTSTRAP_ADMIN_PASSWORD':self.password}
        name=self.name+'-bootstrap';self.containers.append(name)
        docker('run','--name',name,'--network',self.name,*sum([['-e',k] for k in env],[]),'nigson/keycloak:qualification','bootstrap-admin','user',env=env)
        self.node1=self.node('node1');self.node2=self.node('node2');self.proxy()
        self.wait_http(self.admin_origin+'/realms/master/.well-known/openid-configuration')
        for node in ('node1','node2'): self.wait_http(f'https://localhost:{self.management[node]}/health/ready')
        self.admin=Admin(self.admin_origin,self.ca);self.admin.login('offline-bootstrap',self.password)
        policy=realm_policy(self.realm,self.callback,1,'IDP-001-offline-qualification','test',3600)
        self.admin.provision(policy,self.secrets);self.admin.provision(policy,self.secrets)
        for realm in (self.realm, 'master'):
            current = self.admin.request('/admin/realms/' + realm)
            assert current['eventsEnabled'] and current['adminEventsEnabled'] and not current['adminEventsDetailsEnabled']
            assert current['eventsExpiration'] == 3600 and current['attributes']['adminEventsExpiration'] == '3600'
        records = self.admin.request('/admin/realms/' + self.realm + '/admin-events?max=1')
        assert records and not records[0].get('representation')
        self.passed('TEST-KEYCLOAK-AUDIT-POLICY')
        self.users();self.permanent_admin();self.passed('TEST-KEYCLOAK-PROD-CONFIG')
        public=Admin(self.origin,self.ca)
        for path in ('/admin/','/admin/realms','/realms/master/.well-known/openid-configuration','/health/ready','/metrics','/realms/'+self.realm+'/../../admin/','/realms/'+self.realm+'/%2e%2e/%2e%2e/admin/'):
            try: public.request(path);raise AssertionError('PUBLIC_ADMIN_REACHABLE')
            except ValueError as failure: assert str(failure) in {'ADMIN_HTTP_REJECTED_403','ADMIN_HTTP_REJECTED_404'}
        self.passed('TEST-KEYCLOAK-ADMIN-ISOLATION')
        self.browser_login('offline-retail',invalid_pkce=True)
        self.browser_login('offline-retail',code_replay=True)
        self.retail=self.browser_login('offline-retail');self.browser_login('offline-staff',True)
        self.passed('TEST-KEYCLOAK-PKCE-STAFF-MFA')
        workload=self.token({'grant_type':'client_credentials','client_id':'nigson-integration','client_secret':self.secrets['nigson-integration']})
        private(self.work/'tokens.json',json.dumps({'issuer':self.origin+'/realms/'+self.realm,'jwks':self.origin+'/realms/'+self.realm+'/protocol/openid-connect/certs','ca':self.ca,'retail':self.retail,'workload':workload}))
        self.validate_tokens()
        docker('stop','--time','10',self.node1)
        time.sleep(8)
        self.wait_http(self.origin+'/realms/'+self.realm+'/.well-known/openid-configuration')
        refreshed=self.token({'grant_type':'refresh_token','client_id':'nigson-bff','client_secret':self.secrets['nigson-bff'],'refresh_token':self.retail['refresh_token']})
        replay=self.browser_login('offline-retail')
        replay_request={'grant_type':'refresh_token','client_id':'nigson-bff','client_secret':self.secrets['nigson-bff'],'refresh_token':replay['refresh_token']}
        self.token(replay_request)
        try: self.token(replay_request);raise AssertionError('REFRESH_REPLAY_ACCEPTED')
        except ValueError as failure: assert str(failure)=='ADMIN_HTTP_REJECTED_400'
        self.retail=refreshed;self.passed('TEST-KEYCLOAK-REPLICA')
        docker('rm',self.node1);self.node1=self.node('node1');time.sleep(15)
        self.browser_login('offline-retail');self.passed('TEST-KEYCLOAK-ROLLING-REPLACEMENT')
        self.restore()
    def validate_tokens(self):
        env={'NIGSON_IDENTITY_FIXTURE':str(self.work/'tokens.json'),'JAVA_HOME':'/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home'}
        result=subprocess.run([str(ROOT/'mvnw'),'-B','-pl','services/commerce','-am','test','-Dtest=KeycloakTrustTest','-Dsurefire.failIfNoSpecifiedTests=false'],cwd=ROOT,env={**os.environ,**env},capture_output=True,timeout=180)
        private(self.work/'java-trust.log',result.stdout+result.stderr)
        if result.returncode: raise RuntimeError('JAVA_IDENTITY_TRUST_FAILED')
        self.passed('TEST-SEC-TOKENS')
        if os.environ.get('PROFILE')=='ai':
            result=subprocess.run([str(ROOT/'.build/ai-env/bin/pytest'),'services/ai/tests/test_identity.py','-q'],cwd=ROOT,env={**os.environ,**env},capture_output=True,timeout=90)
            private(self.work/'python-trust.log',result.stdout+result.stderr)
            if result.returncode: raise RuntimeError('PYTHON_IDENTITY_TRUST_FAILED')
            self.passed('TEST-SEC-TOKENS-BP5')
        if os.environ.get('NIGSON_SEC_IDENTITY') == 'true':
            self.security_identity()
        self.metrics()

    def security_identity(self):
        # Codes are issued immediately before the native Java test; raw material stays private.
        fixture = {'issuer':self.origin+'/realms/'+self.realm, 'ca':self.ca, 'callback':self.callback, 'bff_secret':self.secrets['nigson-bff'], 'workload_secret':self.secrets['nigson-integration'], 'retail':self.browser_login('offline-retail',emit_code=True), 'staff':self.browser_login('offline-staff',staff=True,emit_code=True)}
        private(self.work/'sec-identity.json',json.dumps(fixture))
        env={**os.environ,'NIGSON_SEC_IDENTITY_FIXTURE':str(self.work/'sec-identity.json'),'JAVA_HOME':'/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home'}
        result=subprocess.run([str(ROOT/'mvnw'),'-B','-pl','services/commerce','-am','test','-Dtest=OidcSessionAcceptanceTest','-Dsurefire.failIfNoSpecifiedTests=false'],cwd=ROOT,env=env,capture_output=True,timeout=180)
        private(self.work/'sec-identity-test.log',result.stdout+result.stderr)
        if result.returncode:raise RuntimeError('SEC_IDENTITY_ACCEPTANCE_FAILED')
        self.passed('TEST-SEC-OIDC-JDBC-SESSION')

    def metrics(self):
        http=urllib.request.build_opener(urllib.request.ProxyHandler({}),urllib.request.HTTPSHandler(context=ssl.create_default_context(cafile=self.ca)))
        found=False
        for node in ('node1','node2'):
            with http.open(f'https://localhost:{self.management[node]}/metrics',timeout=5) as response:
                raw=response.read(1048576).decode()
                found=found or 'keycloak_user_events_total{' in raw
        assert found,'IDENTITY_EVENT_METRICS_ABSENT'
        self.passed('TEST-KEYCLOAK-PRIVATE-METRICS')

    def restore(self):
        old=self.admin.request('/realms/'+self.realm+'/protocol/openid-connect/certs')
        docker('stop','--time','10',self.node1,self.node2)
        raw=docker('exec','-e','PGPASSWORD',self.pg,'pg_dump','-U','nigson_identity','-d','keycloak','-Fc','--no-owner',env={'PGPASSWORD':self.password})
        private(self.work/'identity.dump',raw);private(self.work/'backup.key',secrets.token_bytes(32))
        encrypt(self.work/'identity.dump',self.work/'identity.encrypted',self.work/'backup.key','IDP-001-offline-qualification')
        damaged=bytearray((self.work/'identity.encrypted').read_bytes());damaged[-1]^=1;private(self.work/'tampered.encrypted',damaged)
        try:
            decrypt(self.work/'tampered.encrypted',self.work/'tampered.dump',self.work/'backup.key','IDP-001-offline-qualification');raise AssertionError('BACKUP_TAMPERING_ACCEPTED')
        except Exception:
            assert not (self.work/'tampered.dump').exists()
        decrypt(self.work/'identity.encrypted',self.work/'restored.dump',self.work/'backup.key','IDP-001-offline-qualification')
        restoredport=port();script='cp /cert/server.key /tmp/server.key; cp /cert/server.crt /tmp/server.crt; chown postgres:postgres /tmp/server.key /tmp/server.crt; chmod 600 /tmp/server.key; exec docker-entrypoint.sh postgres -c ssl=on -c ssl_cert_file=/tmp/server.crt -c ssl_key_file=/tmp/server.key -c log_min_error_statement=panic'
        restored=self.run('restored',PG,['-ec',script],{'POSTGRES_PASSWORD':self.password},['--entrypoint','bash','-p',f'127.0.0.1:{restoredport}:5432','-v',f'{self.work}:/cert:ro'])
        bootstrap(self.db_ready(restoredport),self.password,'test')
        docker('exec','-i','-e','PGPASSWORD',restored,'pg_restore','-U','nigson_identity','-d','keycloak','--no-owner','--exit-on-error',env={'PGPASSWORD':self.password},data=(self.work/'restored.dump').read_bytes())
        docker('rm',self.node1,self.node2)
        self.node1=self.node('node1','restored');self.node2=self.node('node2','restored')
        # Resolve new upstream addresses after replacement; only the isolated proxy is restarted.
        docker('restart',self.name+'-proxy');self.wait_http(self.admin_origin+'/realms/master/.well-known/openid-configuration')
        self.admin.login('offline-permanent',self.password,self.otp(self.admin_otp))
        current=self.admin.request('/realms/'+self.realm+'/protocol/openid-connect/certs')
        assert sorted(x['kid'] for x in old['keys'])==sorted(x['kid'] for x in current['keys'])
        self.token({'grant_type':'refresh_token','client_id':'nigson-bff','client_secret':self.secrets['nigson-bff'],'refresh_token':self.retail['refresh_token']})
        self.browser_login('offline-retail');self.passed('TEST-IDENTITY-RESTORE')
    def close(self):
        for name in self.containers:
            try:
                raw=docker('logs',name).decode()
                # Vendor logs are private diagnostic artifacts; never commit raw logs or identities.
                private(self.work/(name+'.log'),raw)
                docker('rm','-f','-v',name)
            except Exception: pass
        try: docker('network','rm',self.name)
        except Exception: pass
        after=snapshot();assert after==self.before,'ORIGINAL_LOCAL_STATE_CHANGED'
        evidence=ROOT/'docs/implementation/evidence/2026-10-09-IDP-001';evidence.mkdir(parents=True,exist_ok=True)
        (evidence/(self.name+'.json')).write_text(json.dumps({'project':self.name,'checks':self.checks,'status':'PASS' if 'TEST-IDENTITY-RESTORE' in self.checks else 'FAIL','originalContainersPreserved':True,'original':after,'cloudQualification':'NOT_RUN','acceptanceComplete':'TEST-IDENTITY-RESTORE' in self.checks},indent=2)+'\n')
        print('PASS ORIGINAL-NIGSON-LOCAL-PRESERVED',flush=True)


if __name__=='__main__':
    qualification=Qualification()
    try: qualification.execute()
    finally: qualification.close()
