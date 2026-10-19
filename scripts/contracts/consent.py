"""EXP-CONSE-001 newsletter, consent and notification-context contract extension."""
def extend_experience(spec):
    from authorization import object_schema, operation, parameter, UUID, REF
    schemas=spec['components']['schemas'];paths=spec['paths']
    schemas['NewsletterSubscribe']=object_schema({'email':{'type':'string','format':'email','maxLength':254},'policyVersion':{'type':'string','pattern':'^[A-Za-z0-9][A-Za-z0-9._:-]{0,39}$'},'antiSpamToken':{'type':'string','minLength':1,'maxLength':512}})
    schemas['NewsletterAccepted']=object_schema({'accepted':{'type':'boolean','const':True}})
    schemas['NewsletterCapability']=object_schema({'token':{'type':'string','minLength':1,'maxLength':512}})
    schemas['NewsletterConfirmation']=object_schema({'confirmed':{'type':'boolean'}})
    schemas['NewsletterUnsubscribed']=object_schema({'unsubscribed':{'type':'boolean','const':True}})
    schemas['ConsentUpdate']=object_schema({'destinationId':UUID,'channel':{'enum':['EMAIL','SMS','WHATSAPP']},'purpose':{'enum':['NEWSLETTER','MARKETING','CART_RECOVERY','ORDER_UPDATES']},'granted':{'type':'boolean'},'policyVersion':{'type':'string','pattern':'^[A-Za-z0-9][A-Za-z0-9._:-]{0,39}$'}})
    schemas['ConsentUpdate']['required'].remove('destinationId')
    schemas['ConsentResult']=object_schema({'channel':{'enum':['EMAIL','SMS','WHATSAPP']},'purpose':{'enum':['NEWSLETTER','MARKETING','CART_RECOVERY','ORDER_UPDATES']},'granted':{'type':'boolean'},'recordedAt':{'type':'string','format':'date-time'}})
    schemas['NewsletterNotificationContext']=object_schema({'email':{'type':'string','format':'email','maxLength':254},'confirmationLink':{'anyOf':[{'type':'string','format':'uri','pattern':'^https://'}, {'type':'null'}]},'unsubscribeLink':{'type':'string','format':'uri','pattern':'^https://'},'classification':{'const':'TRANSACTIONAL'}})
    schemas['NewsletterNotificationJob']=object_schema({'jobId':UUID})
    def command(api,description,response,method,path,body,headers=(),status=200,security=None):
        op=operation(api,description,response,list(headers));op['requestBody']={'required':True,'content':{'application/json':{'schema':REF(body)}}}
        if security:op['security']=security
        if status!=200:op['responses'][str(status)]=op['responses'].pop('200')
        op['responses'][str(status)].setdefault('headers',{})['Cache-Control']={'schema':{'const':'no-store'}}
        paths[path]={method:op};return op
    key=parameter('Idempotency-Key','header',{'type':'string','minLength':1,'maxLength':100},True)
    command('API-099','Neutral 202 response prevents email enumeration. Anti-abuse checked before durable event; pending subscription and EVT-022 commit atomically. Double opt-in is required; this request does not grant marketing eligibility.','NewsletterAccepted','post','/api/v1/newsletter-subscriptions','NewsletterSubscribe',[key],202)
    command('API-100','One-use 24-hour opaque capability. Invalid, expired and replayed capabilities return a bounded negative result; a verified grant, latest consent event and EVT-023 commit atomically.','NewsletterConfirmation','post','/api/v1/newsletter-subscriptions/confirmation','NewsletterCapability',[key])
    command('API-101','Generic idempotent unsubscribe acknowledgment. Valid withdrawal appends consent evidence and creates authoritative EMAIL suppression in the same local transaction, before provider synchronization.','NewsletterUnsubscribed','post','/api/v1/unsubscribe','NewsletterCapability',[key])
    command('API-102','Exact Commerce signed CUSTOMER actor. If-Match protects the owned email or verified phone destination revision. A withdrawal writes channel suppression before any asynchronous provider synchronization.','ConsentResult','put','/api/v1/me/consents','ConsentUpdate',[parameter('If-Match','header',{'type':'string','pattern':'^"(0|[1-9][0-9]{0,18})"$'},True),key],200,[{'workload':['consent.write_WITH_SIGNED_CUSTOMER'],'signedActor':[]}])
    command('API-158','Exact Integration workload; stable jobId binds encrypted recipient/link context. Retries return the same unexpired context; raw capability values are never logged or stored outside authenticated ciphertext.','NewsletterNotificationContext','post','/internal/v1/newsletter-subscriptions/{id}/notification-context','NewsletterNotificationJob',[parameter('id','path',UUID,True)],200,[{'workload':['notification.read']}])
    return spec
