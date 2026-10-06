"""Explicit genuine read-only qualification; never invokes mocks or creates external charges/sends."""
import os
from pathlib import Path
import subprocess
ROOT=Path(__file__).resolve().parents[2]
if os.environ.get('ENV')!='stage':raise SystemExit('BLOCKED: provider-live-check requires explicit ENV=stage; no provider call made')
provider=os.environ.get('PROVIDER','all')
keys={
 'paystack':('PAYSTACK_MODE','PAYSTACK_BASE_URL','PAYSTACK_SECRET_KEY','PAYSTACK_QUALIFICATION_REFERENCE','PAYSTACK_QUALIFICATION_KOBO'),
 'fedex':('FEDEX_MODE','FEDEX_BASE_URL','FEDEX_CLIENT_ID','FEDEX_CLIENT_SECRET','FEDEX_ACCOUNT_NUMBER','FEDEX_QUALIFICATION_REQUEST'),
 'twilio':('TWILIO_MODE','TWILIO_BASE_URL','TWILIO_ACCOUNT_SID','TWILIO_AUTH_TOKEN','TWILIO_MESSAGING_SERVICE_SID','TWILIO_QUALIFICATION_MESSAGE_SID')}
if provider not in (*keys,'all'):raise SystemExit('BLOCKED: unknown PROVIDER; no provider call made')
for name in keys if provider=='all' else (provider,):
 for key in keys[name]:
  value=os.environ.get(key,'')
  if not value or any(word in value.lower() for word in ('mock','dummy','placeholder','example.invalid')):
   raise SystemExit('BLOCKED: genuine '+key+' required; no provider call made')
subprocess.run([str(ROOT/'mvnw'),'-B','verify','dependency:build-classpath','-Dmdep.outputFile=target/provider-classpath.txt'],cwd=ROOT,check=True,timeout=300)
cp=(ROOT/'services/integration/target/provider-classpath.txt').read_text().strip()
cp=':'.join([str(ROOT/'.build/java'/o/'classes') for o in ('commerce','integration','common-events','common-security')])+':'+cp
target=ROOT/'tests/providers/target';target.mkdir(exist_ok=True)
java=Path(os.environ['JAVA_HOME'])/'bin'
subprocess.run([str(java/'javac'),'-cp',cp,'-d',str(target),str(ROOT/'tests/providers/src/com/nigson/providers/ProviderLiveQualification.java')],cwd=ROOT,check=True)
subprocess.run([str(java/'java'),'-cp',str(target)+':'+cp,'com.nigson.providers.ProviderLiveQualification'],cwd=ROOT,check=True,timeout=120)
