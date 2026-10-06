"""Run bounded test-only mock controls on the explicitly local Compose network."""
import argparse
import json
import os
from dependencies import command, validate
parser=argparse.ArgumentParser(description=__doc__)
parser.add_argument('action',choices=('reset','configure','state'))
args=parser.parse_args()
os.environ['PROFILES']='provider-mocks'
validate()
provider=os.environ.get('PROVIDER','all')
if provider not in ('paystack','fedex','twilio','all'):raise ValueError('PROVIDER must be paystack, fedex, twilio or all')
scenario=os.environ.get('SCENARIO','success')
if scenario not in ('success','decline','rate-limit','unavailable','timeout-after-acceptance','booking-response-lost','duplicate-status','out-of-order-status'):raise ValueError('Unregistered SCENARIO')
for item in ('paystack','fedex','twilio') if provider=='all' else (provider,):
 command('exec','-T',item+'-mock','java','-cp','/var/wiremock/extensions/provider-state.jar','com.nigson.mocks.AdminClient',args.action,json.dumps({'scenario':scenario}) if args.action=='configure' else '{}')
