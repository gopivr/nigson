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
def staff_reply_extend_commerce(spec):
    from authorization import object_schema, operation, parameter, UUID, REF
    model=spec['components']['schemas']
    model['StaffLeadReply']=object_schema({'templateCode':{'type':'string','const':'STAFF_LEAD_REPLY'},'channel':{'type':'string','const':'WHATSAPP'},'variables':{'type':'object','maxProperties':20,'propertyNames':{'type':'string','pattern':'^[A-Za-z0-9_]{1,40}$'},'additionalProperties':{'type':'string','maxLength':1000}}})
    model['StaffLeadReplyAcknowledgement']=object_schema({'jobId':UUID,'state':{'type':'string','const':'PENDING'}})
    op=operation('API-192','ADR-020/021: current Commerce BFF SALES role and session-bound API-194/API-193 actor assertions. CSRF and Idempotency-Key required; fixed Integration relay only, no phone or provider input. Returns durable pending job after Integration commits encrypted recipient/context and command identity.','StaffLeadReplyAcknowledgement',[parameter('id','path',UUID,True),parameter('Idempotency-Key','header',UUID,True),parameter('X-CSRF-TOKEN','header',{'type':'string','minLength':43,'maxLength':43},True)])
    op['requestBody']={'required':True,'content':{'application/json':{'schema':REF('StaffLeadReply')}}};op['responses']['202']=op['responses'].pop('200');op['responses']['202']['headers']={'Cache-Control':{'schema':{'const':'no-store'}}}
    spec['paths']['/api/v1/staff/leads/{id}/messages']={'post':op};return spec
def quote_lead_extend_commerce(spec):
    # API-130/131 are Experience-owned operations and are generated there.
    return spec

def lead_extend_experience(spec):
    from authorization import object_schema, operation, parameter, UUID, REF
    model=spec['components']['schemas'];key=parameter('Idempotency-Key','header',{'type':'string','minLength':1,'maxLength':100,'pattern':r'^[ -~]+$'},True);path=parameter('id','path',UUID,True)
    model['QuoteRequestProduct']=object_schema({'skuId':UUID,'quantity':{'type':'string','pattern':r'^[0-9]{1,17}(\.[0-9]{1,3})?$'}})
    model['QuoteRequestContact']=object_schema({'name':{'type':'string','minLength':1,'maxLength':160},'email':{'type':'string','format':'email','maxLength':254},'phone':{'type':'string','maxLength':32}});model['QuoteRequestContact']['required']=['name','email']
    model['QuoteRequestInput']=object_schema({'marketId':UUID,'organizationId':{'anyOf':[UUID,{'type':'null'}]},'products':{'type':'array','minItems':1,'maxItems':50,'items':REF('QuoteRequestProduct'),'uniqueItems':True},'subject':{'type':'string','minLength':1,'maxLength':200},'message':{'type':'string','minLength':1,'maxLength':10000},'contact':REF('QuoteRequestContact')});model['QuoteRequestInput']['required']=['marketId','products','subject','message','contact']
    model['QuoteLeadCreated']=object_schema({'leadId':UUID,'state':{'const':'NEW'}})
    model['QuoteLeadContext']=object_schema({'id':UUID,'partyId':{'anyOf':[UUID,{'type':'null'}]},'organizationId':{'anyOf':[UUID,{'type':'null'}]},'state':{'type':'string','maxLength':40}})
    model['QuoteLeadAssociation']=object_schema({'commandId':UUID,'quoteId':UUID,'quoteState':{'enum':['DRAFT','OFFERED','ACCEPTED','REJECTED','EXPIRED','CANCELLED']}})
    model['QuoteLeadAcknowledgement']=object_schema({'acknowledged':{'type':'boolean'}})
    create=operation('API-088','Signed current CUSTOMER quote-request intake. Commerce validates market, active SKU eligibility and organization authority through API-206 before Experience commits lead, normalized requested items, EVT-016, audit and idempotency. Notification delivery is asynchronous from the committed owner outbox; recipient group must be explicitly configured.','QuoteLeadCreated',[key]);create['security']=[{'workload':['lead.quote.create_WITH_SIGNED_CUSTOMER'],'signedActor':[]}];create['requestBody']={'required':True,'content':{'application/json':{'schema':REF('QuoteRequestInput')}}};create['responses']['200']['description']='Created lead and requested items committed';create['responses']['200']['headers']={'Cache-Control':{'schema':{'const':'no-store'}}};create['responses']['201']=create['responses'].pop('200');create['responses']['503']={'description':'Commerce eligibility unavailable; no lead is created; retry the same idempotency key'};spec['paths']['/api/v1/quote-requests']={'post':create}
    read=operation('API-130','Exact Commerce workload only. Return the Experience-owned QUOTE lead identity, organization and current state; never return request PII or cross-owner data.','QuoteLeadContext',[path]);read['security']=[{'workload':['quote.read']}]
    write=operation('API-131','Exact Commerce workload only. Bind one Experience QUOTE lead to one immutable Commerce quote identifier. Command UUID must equal Idempotency-Key; body hash is durable and conflicting identity is rejected.','QuoteLeadAcknowledgement',[path,key]);write['security']=[{'workload':['quote.write']}];write['requestBody']={'required':True,'content':{'application/json':{'schema':REF('QuoteLeadAssociation')}}};write['responses']['200']['headers']={'Cache-Control':{'schema':{'const':'no-store'}}}
    spec['paths']['/internal/v1/leads/{id}/quote-context']={'get':read};spec['paths']['/internal/v1/leads/{id}/quote-association']={'post':write};return spec
def experience_contract():
    from authorization import operation, parameter, object_schema, UUID, REF
    model=schemas()
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
    model['QuoteLeadContext']=object_schema({'id':UUID,'partyId':UUID,'organizationId':{'anyOf':[UUID,{'type':'null'}]},'state':{'type':'string','maxLength':40}})
    model['QuoteLeadAssociation']=object_schema({'commandId':UUID,'quoteId':UUID,'quoteState':{'enum':['DRAFT','OFFERED','ACCEPTED','REJECTED','EXPIRED','CANCELLED']}})
    model['QuoteLeadAcknowledgement']=object_schema({'acknowledged':{'type':'boolean'}})
    model['LeadMessagingContext']=object_schema({'phone':{'type':'string','pattern':r'^\+234[1-9][0-9]{9}$'},'channel':{'const':'WHATSAPP'},'destinationHash':{'type':'string','pattern':'^[a-f0-9]{64}$'},'eligible':{'const':True},'basis':{'enum':['SIGNED_INBOUND_REQUEST','VERIFIED_DESTINATION']},'approvedTemplateCodes':{'type':'array','maxItems':10,'uniqueItems':True,'items':{'enum':['STAFF_LEAD_REPLY']}}})
    model['PhoneEligibilityInput']=object_schema({'partyId':UUID,'destinationId':UUID,'emailHash':{'type':'string','pattern':'^[a-f0-9]{64}$'},'channel':{'enum':['EMAIL','SMS','WHATSAPP']},'purpose':{'enum':['NEWSLETTER','ORDER_UPDATES','MARKETING','CART_RECOVERY']}})
    model['PhoneEligibilityInput']['required']=['channel','purpose'];model['PhoneEligibilityInput']['anyOf']=[{'required':['partyId']},{'required':['destinationId']},{'required':['emailHash']}]
    model['PhoneEligibility']=object_schema({'eligible':{'type':'boolean'},'reasonCode':{'enum':['GRANTED','SUPPRESSED','CONSENT_NOT_GRANTED']},'email':{'anyOf':[{'type':'string','format':'email','maxLength':254},{'type':'null'}]},'phone':{'type':['string','null']},'destinationHash':{'type':'string','pattern':'^[a-f0-9]{64}$'},'policyRevision':{'type':'string'}})
    model['PhoneSuppression']=object_schema({'commandId':UUID,'destinationHash':{'type':'string','pattern':'^[a-f0-9]{64}$'},'channel':{'enum':['SMS','WHATSAPP']},'reason':{'enum':['HARD_BOUNCE','COMPLAINT','OPT_OUT']}})
    model['PhoneAcknowledged']=object_schema({'acknowledged':{'type':'boolean'}})
    for api,path,body,response,scope in [('API-127','/internal/v1/communication-eligibility','PhoneEligibilityInput','PhoneEligibility','consent.read'),('API-175','/internal/v1/communication-suppressions','PhoneSuppression','PhoneAcknowledged','consent.write')]:
        op=operation(api,'Exact nigson-integration, no user actor. Resolve the requested email, SMS or WhatsApp destination against current purpose grants and authoritative channel suppression; missing destination denies. No provider HTTP.',response,[])
        op['security']=[{'workload':[scope]}];op['requestBody']={'required':True,'content':{'application/json':{'schema':REF(body)}}};paths[path]={'post':op}
    contract={'openapi':'3.1.0','info':{'title':'Nigson Experience APIs','version':'1.1.0'},'paths':paths,'components':{'schemas':model,'securitySchemes':{'workload':{'type':'oauth2','flows':{'clientCredentials':{'tokenUrl':'https://configured-keycloak.invalid/protocol/openid-connect/token','scopes':{**{f'communication.{scope}_WITH_SIGNED_CUSTOMER':'Exact commerce private phone operation' for scope in ['create','verify','read','withdraw']},'consent.write_WITH_SIGNED_CUSTOMER':'Current customer email or verified phone consent change','notification.read':'Original private verification context','notification.read_WITH_SIGNED_ACTOR':'Lead reply context for a signed SALES actor','consent.read':'Current consent eligibility','consent.write':'Verified provider suppression','quote.read':'Read Experience-owned quote lead context','quote.write':'Bind Experience-owned quote lead to Commerce quote','lead.quote.create_WITH_SIGNED_CUSTOMER':'Create a quote lead as the signed current CUSTOMER'}}}},'signedActor':{'type':'apiKey','in':'header','name':'X-Nigson-Actor'}}}}
    from career import extend_experience
    from consent import extend_experience as consent_extend
    from engagement import extend_experience as engagement_extend
    from distributor import extend_experience as distributor_extend
    from content_knowledge import extend as content_knowledge_extend
    return content_knowledge_extend(report_extend_experience(audit_extend_experience(lead_extend_experience(distributor_extend(engagement_extend(consent_extend(extend_experience(contract))))) )))

def audit_extend_experience(spec):
    """API-138 Experience-owned read-only audit page; preserves the shared AuditDTO."""
    from authorization import REF, UUID, VERSION, object_schema, parameter
    nullable=lambda schema:{'anyOf':[schema,{'type':'null'}]}
    schemas=spec['components']['schemas']
    schemas['AuditDTO']=object_schema({'id':UUID,'actor':{'type':'string','minLength':1,'maxLength':200},'action':{'type':'string','minLength':1,'maxLength':100},'resourceType':{'type':'string','minLength':1,'maxLength':80},'resourceId':UUID,'occurredAt':{'type':'string','format':'date-time'},'reason':nullable({'type':'string','maxLength':500}),'oldValue':nullable({'type':'object','additionalProperties':True}),'newValue':nullable({'type':'object','additionalProperties':True})})
    schemas['AuditPage']=object_schema({'items':{'type':'array','maxItems':100,'items':REF('AuditDTO')},'page':{'type':'integer','minimum':0},'size':{'type':'integer','minimum':1,'maximum':100},'totalElements':VERSION,'totalPages':VERSION})
    path='/api/v1/experience/staff/audit-records'
    spec['components']['securitySchemes']['workload']['flows']['clientCredentials']['scopes']['audit.read_WITH_SIGNED_ADMIN']='Read Experience-owned audit history with a signed current ADMIN actor'
    spec['components']['securitySchemes']['signedActor']={'type':'apiKey','in':'header','name':'X-Nigson-Actor'}
    spec['paths'][path]={'get':{'operationId':'API-138','description':'Exact nigson-commerce workload client and audit.read_WITH_SIGNED_ADMIN scope with signed ADMIN actor proof. The proof is issued by the Commerce BFF after current ADMIN authorization; organization-scoped actors are denied. Read only Experience.audit_record. Bound filters use resourceId, action and optional UTC from/to (when both are present, the positive interval is at most 366 days). Reject unknown/repeated parameters, request bodies, invalid ranges and non-allowlisted sorts. Stable ordering uses audit id as final tie-breaker. Pages are bounded to 100 rows. Persisted JSON is redacted again at read time. Response is no-store. This GET is append-only-safe and does not write audit, idempotency or outbox state; no retention/access policy is inferred.','security':[{'workload':['audit.read_WITH_SIGNED_ADMIN']}],'parameters':[parameter('resourceId','query',UUID),parameter('action','query',{'type':'string','pattern':'^[A-Z][A-Z0-9_]{0,99}$','maxLength':100}),parameter('from','query',{'type':'string','format':'date-time','maxLength':40}),parameter('to','query',{'type':'string','format':'date-time','maxLength':40}),parameter('page','query',{'type':'integer','minimum':0,'default':0}),parameter('size','query',{'type':'integer','minimum':1,'maximum':100,'default':20}),parameter('sort','query',{'type':'string','enum':['occurredAt,desc','occurredAt,asc','actor,asc','actor,desc','action,asc','action,desc','resourceType,asc','resourceType,desc'],'default':'occurredAt,desc'})],'responses':{'200':{'description':'Sanitized Experience-owned audit page','headers':{'Cache-Control':{'schema':{'const':'no-store'}}},'content':{'application/json':{'schema':REF('AuditPage')}}},**{str(code):{'description':desc,'content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}} for code,desc in [(400,'Invalid query'),(401,'Authentication required'),(403,'Access denied'),(500,'Safe internal error')]}}}}
    return spec

def report_extend_experience(spec):
    """API-110 sanitized Experience-owned engagement aggregates."""
    from authorization import REF, UUID, object_schema, parameter
    schemas=spec['components']['schemas']
    state_counts={'type':'object','maxProperties':10,'additionalProperties':{'type':'integer','minimum':0}}
    schemas['ExperienceCampaignEntries']=object_schema({'id':UUID,'entries':{'type':'integer','minimum':0}})
    schemas['ExperienceEngagementReport']=object_schema({'leadsByState':state_counts,'applicationsByState':state_counts,'reviewsByState':state_counts,'subscriptions':{'type':'integer','minimum':0},'campaigns':{'type':'array','maxItems':100,'items':REF('ExperienceCampaignEntries')}})
    spec['components']['securitySchemes']['workload']['flows']['clientCredentials']['scopes']['report.read_WITH_SIGNED_ANALYST']='Read sanitized Experience engagement aggregates with a signed current ANALYST actor'
    path='/api/v1/staff/reports/engagement'
    spec['paths'][path]={'get':{'operationId':'API-110','description':'Exact nigson-commerce workload and report.read_WITH_SIGNED_ANALYST scope with signed ANALYST actor proof issued after current staff authorization. Organization-scoped actors are denied because this report has no organization filter. Aggregate only Experience-owned lead, career application, review, newsletter subscription and campaign records over the required UTC [from,to) window (maximum 366 days). Does not return applicant/contact content. The Experience schema stores campaign definitions but no participation records, so entries is zero for currently untracked participation; the service does not infer Commerce reward or wheel data. Reject repeated/unknown parameters and request bodies. Response is no-store; successful reads append a minimal owner-local audit row and bounded metric. Read has no idempotency, If-Match or outbox side effect.','security':[{'workload':['report.read_WITH_SIGNED_ANALYST'],'signedActor':[]}],'parameters':[parameter('from','query',{'type':'string','format':'date-time','maxLength':40},True),parameter('to','query',{'type':'string','format':'date-time','maxLength':40},True)],'responses':{'200':{'description':'Sanitized Experience engagement summary','headers':{'Cache-Control':{'schema':{'const':'no-store'}}},'content':{'application/json':{'schema':REF('ExperienceEngagementReport')}}},**{str(code):{'description':desc,'content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}} for code,desc in [(400,'Invalid range or query'),(401,'Authentication required'),(403,'Access denied'),(500,'Safe internal error')]}}}}
    return spec

def main():
    parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args();path=ROOT/'contracts/openapi/experience.yaml';expected=json.dumps(experience_contract(),indent=2)+'\n'
    if args.check:
        if not path.exists() or path.read_text()!=expected:raise SystemExit('FAIL: Experience API contract drift')
        print('PASS: Experience API contract matches generator')
    else:path.write_text(expected)
if __name__=='__main__':main()
