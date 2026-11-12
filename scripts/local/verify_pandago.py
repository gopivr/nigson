#!/usr/bin/env python3
"""Read-only Delivery Hero OAuth/outlet qualification; never creates deliveries."""
import base64,json,os,re,subprocess,time,urllib.parse,uuid
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def main():
 path=ROOT/'.local/pandago/account.env'
 if path.stat().st_mode & 0o077:raise ValueError('Private account file permissions must be 600')
 env=dict(line.split('=',1) for line in path.read_text().splitlines() if line and not line.startswith('#'))
 mode=env['PANDAGO_MODE'];targets={'LIVE':('https://sts.deliveryhero.io','https://ondemand-api-glovoapp.deliveryhero.io'),'TEST':('https://sts-st.deliveryhero.io','https://api-infra-eu-central-1.stg.ondemandrider.net')}
 if mode not in targets or env['PANDAGO_AUTH_BASE_URL']!=targets[mode][0] or env['PANDAGO_BASE_URL']!=targets[mode][1] or env['PANDAGO_SCOPE']!='pandago.api.ng.*':raise ValueError('Confirmed Nigeria mode/targets required')
 key=ROOT/env['PANDAGO_PRIVATE_KEY_FILE']
 if not key.is_file() or key.stat().st_mode & 0o077:raise ValueError('Private key permissions must be 600')
 public=subprocess.run(['openssl','rsa','-in',str(key),'-pubout'],capture_output=True,check=False)
 info=subprocess.run(['openssl','rsa','-pubin','-text','-noout'],input=public.stdout,capture_output=True,check=False)
 bits=re.search(rb'(?:Public-Key|Modulus):\s*\(([0-9]+) bit',info.stdout)
 if public.returncode or info.returncode or not bits or int(bits[1])<2048:raise ValueError('Registered RSA key must be at least 2048 bits; provider key registration must match')
 encode=lambda value:base64.urlsafe_b64encode(value).decode().rstrip('=')
 now=int(time.time());header={'alg':'RS256','typ':'JWT','kid':env['PANDAGO_KEY_ID']};claims={'iss':env['PANDAGO_CLIENT_ID'],'sub':env['PANDAGO_CLIENT_ID'],'aud':'https://sts.deliveryhero.io','iat':now,'exp':now+60,'jti':str(uuid.uuid4())}
 signing=(encode(json.dumps(header,separators=(',',':')).encode())+'.'+encode(json.dumps(claims,separators=(',',':')).encode())).encode()
 signed=subprocess.run(['openssl','dgst','-sha256','-sign',str(key)],input=signing,capture_output=True,check=False)
 if signed.returncode:raise ValueError('Private key signing failed')
 assertion=signing.decode()+'.'+encode(signed.stdout)
 def request(url,data=None,token=None):
  command=['curl','--silent','--show-error','--proto','=https','--connect-timeout','3','--max-time','15','--max-filesize','1048576','--write-out','\n%{http_code}','--header','Accept: application/json','--user-agent','Nigson-qualification/1.0']
  # Secrets are provided on stdin, never shell command text or process arguments.
  if data is not None:command+=['--request','POST','--header','Content-Type: application/x-www-form-urlencoded','--data-binary','@-'];input=data
  elif token:command+=['--request','GET','--header','@-'];input=('Authorization: Bearer '+token+'\n').encode()
  else:input=None
  result=subprocess.run(command+[url],input=input,capture_output=True,check=False)
  if result.returncode:raise ValueError('Provider network/TLS request failed (curl '+str(result.returncode)+')')
  raw,status=result.stdout.rsplit(b'\n',1);status=int(status)
  if len(raw)>1048576:raise ValueError('Provider response too large')
  try:body=json.loads(raw)
  except ValueError:raise ValueError('Invalid provider JSON response') from None
  if status<200 or status>=300:
   candidate=body.get('error','');code=candidate if isinstance(candidate,str) and re.fullmatch('[A-Za-z0-9_-]{1,80}',candidate) else ''
   raise ValueError('Provider rejected request: HTTP '+str(status)+(' '+code if code else ''))
  return status,body
 body=urllib.parse.urlencode({'grant_type':'client_credentials','client_id':env['PANDAGO_CLIENT_ID'],'client_assertion_type':'urn:ietf:params:oauth:client-assertion-type:jwt-bearer','client_assertion':assertion,'scope':env['PANDAGO_SCOPE']}).encode()
 status,auth=request(env['PANDAGO_AUTH_BASE_URL']+'/oauth2/token',body)
 token=auth.get('access_token')
 if status!=200 or not isinstance(token,str) or not 1<=len(token)<=16384 or not isinstance(auth.get('expires_in'),int) or auth['expires_in']<=0:raise ValueError('Invalid OAuth response')
 print('PASS: Delivery Hero '+mode+' OAuth authenticated.',flush=True)
 status,outlet=request(env['PANDAGO_BASE_URL']+'/ng/api/v1/outlets/'+urllib.parse.quote(env['PANDAGO_CLIENT_VENDOR_ID'],safe=''),token=token)
 if status!=200 or outlet.get('client_vendor_id')!=env['PANDAGO_CLIENT_VENDOR_ID'] or outlet.get('currency')!='NGN':raise ValueError('Nigeria outlet identity/currency mismatch')
 target=ROOT/'.local/pandago/outlet.json';target.write_text(json.dumps(outlet));os.chmod(target,0o600)
 print('PASS: registered NGN outlet verified; no quote, booking or cancellation submitted.')
if __name__=='__main__':
 try:main()
 except Exception as error:
  print('FAIL: '+(str(error) if isinstance(error,ValueError) else type(error).__name__))
  raise SystemExit(1)
