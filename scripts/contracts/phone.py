"""Implemented phone destination BFF relay and experience-owned private proof contracts."""
import json
import argparse
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
def schemas():
    from authorization import object_schema, UUID, VERSION
    channel={'enum':['SMS','WHATSAPP']}
    return {'PhoneDestination':object_schema({'id':UUID,'channel':channel,'maskedAddress':{'type':'string','pattern':r'^\+234\*{6}[0-9]{4}$'},'status':{'enum':['PENDING','ACTIVE','UNSUBSCRIBED','SUPPRESSED']},'version':VERSION}),
            'PhoneDestinationInput':object_schema({'channel':channel,'address':{'type':'string','pattern':r'^\+234[1-9][0-9]{9}$'},'policyVersion':{'type':'string','pattern':'^[A-Za-z0-9][A-Za-z0-9._:-]{0,39}$'},'consentedPurposes':{'type':'array','minItems':1,'maxItems':3,'uniqueItems':True,'items':{'enum':['ORDER_UPDATES','MARKETING','CART_RECOVERY']}}}),
            'PhoneProof':object_schema({'code':{'type':'string','pattern':'^[0-9]{6}$'}}),
            'PhoneProofPrivate':object_schema({'code':{'type':'string','pattern':'^[0-9]{6}$'},'version':VERSION}),
            'PhoneWithdrawal':object_schema({'version':VERSION}),
            'PhoneVerificationJob':object_schema({'jobId':UUID}),
            'PhoneSuppressionCheck':object_schema({'destinationHash':{'type':'string','pattern':'^[a-f0-9]{64}$'},'channel':{'const':'WHATSAPP'}}),
            'PhoneSuppressionResult':object_schema({'suppressed':{'type':'boolean'}}),
            'PhoneVerificationContext':object_schema({'phone':{'type':'string','pattern':r'^\+234[1-9][0-9]{9}$'},'channel':channel,'code':{'type':'string','pattern':'^[0-9]{6}$'},'expiresAt':{'type':'string','format':'date-time'},'templateCode':{'const':'CONTACT_VERIFICATION'}})}
def operations(private=False):
    from authorization import operation, parameter, UUID, REF
    root='/internal/v1/communication-destinations' if private else '/api/v1/me/communication-destinations'
    specs=[(201 if private else 183,'post',root,'PhoneDestinationInput',201,'create'),(202 if private else 184,'post',root+'/{id}/verification','PhoneProofPrivate' if private else 'PhoneProof',200,'verify'),(203 if private else 185,'get',root,None,200,'read'),(204 if private else 186,'post' if private else 'delete',root+'/{id}/withdrawal' if private else root+'/{id}','PhoneWithdrawal' if private else None,204,'withdraw')]
    result={}
    for num,method,path,body,status,scope in specs:
        params=[]
        if '{id}' in path:params.append(parameter('id','path',UUID,True))
        if method!='get':
            params.append(parameter('Idempotency-Key','header',{'type':'string','minLength':1,'maxLength':100},True))
            if not private:params.append(parameter('X-CSRF-TOKEN','header',{'type':'string','minLength':43,'maxLength':43},True))
        if not private and num in {184,186}:params.append(parameter('If-Match','header',{'type':'string','pattern':'^"(0|[1-9][0-9]{0,18})"$'},True))
        op=operation(f'API-{num}','ADR-017. Fixed current CUSTOMER, no organization. Owner proof/consent/suppression; keyed idempotency, five committed failed attempts/10min, three proof requests/hour/destination. No provider HTTP. Withdrawal dominates cached confirmation and channel-purpose grants. Unknown/repeated query or fields rejected. No phone/code in logs/events/audit.','PhoneDestination',params)
        if private:op['security']=[{'workload':[f'communication.{scope}_WITH_SIGNED_CUSTOMER'],'signedActor':[]}]
        success=op['responses'].pop('200');success['headers']={'Cache-Control':{'schema':{'const':'no-store'}}}
        if status==204:success.pop('content')
        elif scope=='read':success['content']['application/json']['schema']={'type':'array','maxItems':100,'items':REF('PhoneDestination')}
        else:success['headers']['ETag']={'schema':{'type':'string'},'description':'Quoted current destination version'}
        op['responses'][str(status)]=success;op['responses']['503']={'description':'Unavailable/uncertain acknowledgement; retry original key/body, never manufacture a fresh proof'}
        if body:op['requestBody']={'required':True,'content':{'application/json':{'schema':REF(body)}}}
        result.setdefault(path,{})[method]=op
    return result
def extend_commerce(spec):
    spec['components']['schemas'].update(schemas());spec['paths'].update(operations());return spec
def experience_contract():
    from authorization import operation, parameter, UUID, REF
    paths=operations(True)
    context194=operation('API-194','Exact Integration workload plus a fresh signed SALES actor. Return only a verified inbound or active, purpose-granted and currently unsuppressed WhatsApp destination for this CONTACT lead; context is limited to an approved transactional reply. No order lookup, marketing authority or caller-supplied destination.','LeadMessagingContext',[parameter('id','path',UUID,True)])
    context194['security']=[{'workload':['notification.read_WITH_SIGNED_ACTOR'],'signedActor':[]}]
    paths['/internal/v1/leads/{id}/messaging-context']={'get':context194}
    suppression205=operation('API-205','Exact Integration workload. Return only current WhatsApp suppression for a keyed destination hash, serializing with STOP; no destination or lead details. The worker must call outside its database transaction immediately before a send and fail closed on unavailable response.','PhoneSuppressionResult',[])
    suppression205['security']=[{'workload':['notification.read']}]
    suppression205['requestBody']={'required':True,'content':{'application/json':{'schema':REF('PhoneSuppressionCheck')}}}
    paths['/internal/v1/notification-suppression-checks']={'post':suppression205}
    paths['/api/v1/webhooks/notifications/twilio/whatsapp']={'post':{
        'operationId':'API-190','summary':'Verified Twilio WhatsApp inbound','description':'Validate the complete signed form against the configured public URL; deduplicate MessageSid and atomically apply STOP/UNSUBSCRIBE suppression, fixed HELP TwiML, or a private CONTACT lead. No browser authentication or CSRF; no order lookup, login, marketing grant, or financial mutation.',
        'security':[],'parameters':[parameter('X-Twilio-Signature','header',{'type':'string','minLength':1,'maxLength':256},True)],
        'requestBody':{'required':True,'content':{'application/x-www-form-urlencoded':{'schema':{'type':'object','properties':{'AccountSid':{'type':'string','pattern':'^AC[0-9a-fA-F]{32}$'},'MessageSid':{'type':'string','pattern':'^SM[0-9a-fA-F]{32}$'},'From':{'type':'string','pattern':'^whatsapp:\\+234[1-9][0-9]{9}$'},'To':{'type':'string','pattern':'^whatsapp:\\+234[1-9][0-9]{9}$'},'Body':{'type':'string','minLength':1,'maxLength':4096}},'required':['AccountSid','MessageSid','From','To','Body'],'additionalProperties':True}}}},
        'responses':{'200':{'description':'Owner commit acknowledged; HELP returns fixed safe TwiML and other dispositions an empty Response.','headers':{'Cache-Control':{'schema':{'const':'no-store'}}},'content':{'application/xml':{'schema':{'type':'string','maxLength':512}}}},'400':{'description':'Malformed bounded callback form'},'403':{'description':'Provider signature or configured account mismatch'},'503':{'description':'Owner transaction unavailable; provider may retry'}}}}
    context=operation('API-187','Exact nigson-integration notification.read, no user actor. Original deterministic contact-verification job UUID only; pending/unexpired/unsuppressed proof. No reissue on replay; encrypted original context only.','PhoneVerificationContext',[parameter('id','path',UUID,True)])
    context['security']=[{'workload':['notification.read']}];context['requestBody']={'required':True,'content':{'application/json':{'schema':REF('PhoneVerificationJob')}}};paths['/internal/v1/communication-verifications/{id}/notification-context']={'post':context}
    from authorization import object_schema
    model=schemas()
    model['LeadMessagingContext']=object_schema({'phone':{'type':'string','pattern':r'^\+234[1-9][0-9]{9}$'},'channel':{'const':'WHATSAPP'},'destinationHash':{'type':'string','pattern':'^[a-f0-9]{64}$'},'eligible':{'const':True},'basis':{'enum':['SIGNED_INBOUND_REQUEST','VERIFIED_DESTINATION']},'approvedTemplateCodes':{'type':'array','maxItems':10,'uniqueItems':True,'items':{'enum':['STAFF_LEAD_REPLY']}}})
    model['PhoneEligibilityInput']=object_schema({'partyId':UUID,'destinationId':UUID,'channel':{'enum':['SMS','WHATSAPP']},'purpose':{'enum':['ORDER_UPDATES','MARKETING','CART_RECOVERY']}})
    model['PhoneEligibilityInput']['required']=['channel','purpose'];model['PhoneEligibilityInput']['anyOf']=[{'required':['partyId']},{'required':['destinationId']}]
    model['PhoneEligibility']=object_schema({'eligible':{'type':'boolean'},'reasonCode':{'enum':['GRANTED','SUPPRESSED','CONSENT_NOT_GRANTED']},'email':{'type':'null'},'phone':{'type':['string','null']},'destinationHash':{'type':'string','pattern':'^[a-f0-9]{64}$'},'policyRevision':{'type':'string'}})
    model['PhoneSuppression']=object_schema({'commandId':UUID,'destinationHash':{'type':'string','pattern':'^[a-f0-9]{64}$'},'channel':{'enum':['SMS','WHATSAPP']},'reason':{'enum':['HARD_BOUNCE','COMPLAINT','OPT_OUT']}})
    model['PhoneAcknowledged']=object_schema({'acknowledged':{'type':'boolean'}})
    for api,path,body,response,scope in [('API-127','/internal/v1/communication-eligibility','PhoneEligibilityInput','PhoneEligibility','consent.read'),('API-175','/internal/v1/communication-suppressions','PhoneSuppression','PhoneAcknowledged','consent.write')]:
        op=operation(api,'Exact nigson-integration, no user actor. Phone-channel implementation only; no email/newsletter activation. Authoritative current grants and global suppression; missing destination denies. No provider HTTP. Suppression command UUID is immutable/idempotent and cannot grant consent.',response,[])
        op['security']=[{'workload':[scope]}];op['requestBody']={'required':True,'content':{'application/json':{'schema':REF(body)}}};paths[path]={'post':op}
    return {'openapi':'3.1.0','info':{'title':'Nigson experience implemented phone consent and messaging','version':'1.1.0'},'paths':paths,'components':{'schemas':model,'securitySchemes':{'workload':{'type':'oauth2','flows':{'clientCredentials':{'tokenUrl':'https://configured-keycloak.invalid/protocol/openid-connect/token','scopes':{**{f'communication.{scope}_WITH_SIGNED_CUSTOMER':'Exact commerce private phone operation' for scope in ['create','verify','read','withdraw']},'notification.read':'Original private verification context','notification.read_WITH_SIGNED_ACTOR':'Lead reply context for a signed SALES actor','consent.read':'Current phone eligibility','consent.write':'Verified provider phone suppression'}}}},'signedActor':{'type':'apiKey','in':'header','name':'X-Nigson-Actor'}}}}
def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args();path=ROOT/'contracts/openapi/experience.yaml';expected=json.dumps(experience_contract(),indent=2)+'\n'
    if args.check:
        if not path.exists() or path.read_text()!=expected:raise SystemExit('FAIL: experience phone contract drift')
        print('PASS: implemented experience phone contract matches generator')
    else:path.write_text(expected)
if __name__=='__main__':main()
