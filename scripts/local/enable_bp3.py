#!/usr/bin/env python3
"""Configure local BP3 service identities using canonical workload scopes.

Uses a temporary recovery admin because existing local bootstrap credentials may
be stale. The temporary account is removed after setup; secrets stay in memory.
"""
import base64,json,os,secrets,subprocess,sys,time,urllib.request,urllib.parse,urllib.error,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/identity'))
from realm_policy import realm_policy


def docker(*args,env=None):
 result=subprocess.run(['docker',*args],cwd=ROOT,env=env,text=True,capture_output=True,timeout=120)
 if result.returncode:raise RuntimeError('Local Docker setup failed: '+' '.join(args[:4]))
 return result.stdout.strip()


def main():
 if os.environ.get('DOCKER_HOST') and not os.environ['DOCKER_HOST'].startswith('unix://'):raise ValueError('Local Docker required')
 if not json.loads(docker('context','inspect'))[0]['Endpoints']['docker']['Host'].startswith('unix://'):raise ValueError('Local Docker required')
 config=json.loads(docker('compose','--profile','app','config','--format','json'))
 if config.get('name')!='nigson-local' or config['services']['commerce']['environment'].get('APP_ENV')!='local':raise ValueError('Local nigson project required')
 port=int(config['services']['keycloak']['ports'][0]['published']);base=f'http://127.0.0.1:{port}'
 class NoRedirect(urllib.request.HTTPRedirectHandler):
  def redirect_request(self,*a,**kw):raise ValueError('Local identity redirect refused')
 http=urllib.request.build_opener(urllib.request.ProxyHandler({}),NoRedirect());admin_token=None
 def request(path,method='GET',data=None,token=None,form=False):
  body=None if data is None else (urllib.parse.urlencode(data) if form else json.dumps(data)).encode();headers={'Content-Type':'application/x-www-form-urlencoded' if form else 'application/json'}
  if token:headers['Authorization']='Bearer '+token
  try:
   with http.open(urllib.request.Request(base+path,data=body,headers=headers,method=method),timeout=10) as r:
    raw=r.read(1048577)
    if len(raw)>1048576:raise ValueError('Identity response too large')
    return json.loads(raw) if raw else None
  except urllib.error.HTTPError as error:raise RuntimeError('Local identity setup refused HTTP '+str(error.code)) from None
 recovery='local-bp3-recovery-'+uuid.uuid4().hex[:10];password=secrets.token_urlsafe(48)
 env={**os.environ,'NIGSON_BP3_RECOVERY_PASSWORD':password};created=False
 try:
  docker('compose','stop','keycloak')
  try:
   docker('compose','run','--rm','-T','--no-deps','-e','NIGSON_BP3_RECOVERY_PASSWORD','--entrypoint','/opt/keycloak/bin/kc.sh','keycloak','bootstrap-admin','user','--username',recovery,'--password:env','NIGSON_BP3_RECOVERY_PASSWORD',env=env);created=True
  finally:docker('compose','up','-d','--no-deps','keycloak')
  for attempt in range(80):
   try:
    admin_token=request('/realms/master/protocol/openid-connect/token','POST',{'grant_type':'password','client_id':'admin-cli','username':recovery,'password':password},form=True)['access_token'];break
   except (OSError,RuntimeError):time.sleep(0.5)
  if not admin_token:raise RuntimeError('Local identity startup timeout')
  prefix='/admin/realms/nigson-local'
  policy=realm_policy('nigson-local','https://local.synthetic.test/bff/callback',3,'local-bp3','test',3600)
  wanted_clients=[c for c in policy['clients'] if c['clientId'] in ('nigson-commerce','nigson-experience')]
  wanted_scopes={name for c in wanted_clients for name in c['defaultClientScopes']}
  existing={scope['name']:scope for scope in request(prefix+'/client-scopes',token=admin_token)}
  def mappers(path,desired):
   current={m['name']:m for m in request(path+'/protocol-mappers/models',token=admin_token)}
   for mapper in desired:
    if mapper['name'] in current:request(path+'/protocol-mappers/models/'+current[mapper['name']]['id'],'PUT',{**mapper,'id':current[mapper['name']]['id']},token=admin_token)
    else:request(path+'/protocol-mappers/models','POST',mapper,token=admin_token)
  for scope in policy['clientScopes']:
   if scope['name'] not in wanted_scopes:continue
   if scope['name'] not in existing:
    request(prefix+'/client-scopes','POST',scope,token=admin_token)
   else:
    request(prefix+'/client-scopes/'+existing[scope['name']]['id'],'PUT',{k:v for k,v in scope.items() if k!='protocolMappers'},token=admin_token)
    mappers(prefix+'/client-scopes/'+existing[scope['name']]['id'],scope['protocolMappers'])
  scopes={scope['name']:scope['id'] for scope in request(prefix+'/client-scopes',token=admin_token)}
  commerce=config['services']['commerce']['environment'];experience=config['services']['experience']['environment']
  client_secrets={'nigson-commerce':commerce['NIGSON_COMMERCE_WORKLOAD_SECRET'],'nigson-experience':experience['NIGSON_DISTRIBUTOR_WORKLOAD_CLIENT_SECRET']}
  for client in wanted_clients:
   found=request(prefix+'/clients?'+urllib.parse.urlencode({'clientId':client['clientId']}),token=admin_token)
   representation={k:v for k,v in client.items() if k not in ('defaultClientScopes','optionalClientScopes','protocolMappers')};representation['secret']=client_secrets[client['clientId']]
   if found:request(prefix+'/clients/'+found[0]['id'],'PUT',representation,token=admin_token)
   else:request(prefix+'/clients','POST',representation,token=admin_token)
   cid=request(prefix+'/clients?'+urllib.parse.urlencode({'clientId':client['clientId']}),token=admin_token)[0]['id'];path=prefix+'/clients/'+cid
   mappers(path,client['protocolMappers']);assigned={s['name'] for s in request(path+'/default-client-scopes',token=admin_token)}
   for name in client['defaultClientScopes']:
    if name not in assigned:request(path+'/default-client-scopes/'+scopes[name],'PUT',token=admin_token)
   result=request('/realms/nigson-local/protocol/openid-connect/token','POST',{'grant_type':'client_credentials','client_id':client['clientId'],'client_secret':client_secrets[client['clientId']]},form=True)
   part=result['access_token'].split('.')[1];claims=json.loads(base64.urlsafe_b64decode(part+'='*(-len(part)%4)))
   if claims['azp']!=client['clientId'] or not set(client['defaultClientScopes']).issubset(set(claims['scope'].split())):raise RuntimeError('Local workload scope verification failed')
   print('PASS:',client['clientId'],'canonical BP3 service scopes and subject mapper configured',flush=True)
 finally:
  if created:
   if not admin_token:
    for attempt in range(20):
     try:admin_token=request('/realms/master/protocol/openid-connect/token','POST',{'grant_type':'password','client_id':'admin-cli','username':recovery,'password':password},form=True)['access_token'];break
     except (OSError,RuntimeError):time.sleep(0.5)
   if not admin_token:raise RuntimeError('Recovery admin cleanup requires local identity availability')
   users=request('/admin/realms/master/users?'+urllib.parse.urlencode({'username':recovery,'exact':'true'}),token=admin_token)
   for user in users:request('/admin/realms/master/users/'+user['id'],'DELETE',token=admin_token)
   print('PASS: temporary recovery admin removed; existing users preserved',flush=True)


if __name__=='__main__':main()
