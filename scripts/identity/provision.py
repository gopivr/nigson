"""Apply approved identity policy through the private admin API; no credential defaults."""
import argparse
import json
from pathlib import Path
from urllib.parse import urlsplit
from admin import Admin
from backup import private_file
from realm_policy import realm_policy


def provision(args):
    path = Path(args.credentials); private_file(path)
    credentials = json.loads(path.read_text())
    origin = urlsplit(args.admin_origin)
    if origin.scheme != 'https' or origin.path or origin.query or origin.fragment or origin.username or not origin.hostname:
        raise ValueError('PRIVATE_ADMIN_ORIGIN_REQUIRED')
    if args.environment != 'test':
        if origin.hostname in {'localhost','127.0.0.1'} or origin.hostname.endswith(('.test','.invalid','.example')):
            raise ValueError('APPROVED_PRIVATE_ADMIN_ORIGIN_REQUIRED')
        values = [credentials['password'], *credentials['clientSecrets'].values()]
        if any(v.startswith(('test-','mock-')) for v in values): raise ValueError('REAL_IDENTITY_CREDENTIALS_REQUIRED')
    policy = realm_policy(args.realm, args.callback, args.phase, args.revision, args.environment, args.audit_retention_seconds)
    admin = Admin(args.admin_origin, args.ca)
    admin.login(credentials['username'], credentials['password'], credentials.get('totp'))
    admin.provision(policy, credentials['clientSecrets'])
    print(json.dumps({'result':'IDENTITY_POLICY_APPLIED','revision':args.revision,'environment':args.environment,'phase':args.phase}))


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    for name in ('realm','callback','revision','environment','admin-origin','ca','credentials'):
        parser.add_argument('--'+name, required=True)
    parser.add_argument('--phase',type=int,required=True)
    parser.add_argument('--audit-retention-seconds',type=int,required=True)
    try: provision(parser.parse_args())
    except Exception: raise SystemExit('IDENTITY_POLICY_APPLICATION_REJECTED') from None
