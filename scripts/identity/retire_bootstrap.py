"""Retire a supplied temporary account only after supplied permanent MFA access succeeds."""
import argparse
import json
from pathlib import Path
from admin import Admin
from backup import private_file


def retire(admin: Admin, permanent_username: str, bootstrap_username: str):
    if not permanent_username or not bootstrap_username or permanent_username == bootstrap_username:
        raise ValueError('DISTINCT_APPROVED_ADMIN_REQUIRED')
    import urllib.parse
    prefix = '/admin/realms/master/users'
    permanent = admin.request(prefix+'?'+urllib.parse.urlencode({'username':permanent_username,'exact':'true'}))
    if len(permanent) != 1 or not permanent[0]['enabled']:
        raise ValueError('PERMANENT_ADMIN_REQUIRED')
    credentials = admin.request(prefix+'/'+permanent[0]['id']+'/credentials')
    if not any(c['type']=='otp' for c in credentials): raise ValueError('PERMANENT_ADMIN_MFA_REQUIRED')
    # Listing master users proves actual vendor administration; identity markers grant no business authority.
    temporary = admin.request(prefix+'?'+urllib.parse.urlencode({'username':bootstrap_username,'exact':'true'}))
    if len(temporary) > 1: raise ValueError('BOOTSTRAP_ACCOUNT_AMBIGUOUS')
    if temporary:
        admin.request(prefix+'/'+temporary[0]['id']+'/logout','POST')
        admin.request(prefix+'/'+temporary[0]['id'],'DELETE')


if __name__=='__main__':
    parser=argparse.ArgumentParser()
    for name in ('admin-origin','ca','credentials','bootstrap-username'):
        parser.add_argument('--'+name,required=True)
    args=parser.parse_args()
    try:
        path=Path(args.credentials);private_file(path);inputs=json.loads(path.read_text())
        admin=Admin(args.admin_origin,args.ca);admin.login(inputs['username'],inputs['password'],inputs['totp'])
        retire(admin,inputs['username'],args.bootstrap_username)
        print('TEMPORARY_BOOTSTRAP_ACCOUNT_RETIRED')
    except Exception: raise SystemExit('BOOTSTRAP_RETIREMENT_REJECTED') from None
