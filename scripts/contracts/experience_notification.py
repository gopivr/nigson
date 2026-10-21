"""API-137 Experience-owned delivery outcome receipt contract."""

def extend_experience(spec):
    from authorization import object_schema, operation, parameter, UUID, REF
    schemas=spec['components']['schemas']
    delivery=object_schema({'jobId':UUID,'resourceId':UUID,'state':{'enum':['SENT','FAILED','SUPPRESSED','UNKNOWN']},'providerReference':{'type':'string','pattern':r'^[A-Za-z0-9][A-Za-z0-9:_-]{0,159}$'}})
    delivery['required'].remove('providerReference')
    schemas['ExperienceNotificationDelivery']=delivery
    schemas['ExperienceNotificationAcknowledged']=object_schema({'acknowledged':{'type':'boolean'}})
    spec['components']['securitySchemes']['workload']['flows']['clientCredentials']['scopes']['notification.write']='Record Experience-owned notification delivery outcomes'
    op=operation('API-137','Exact private nigson-integration notification.write workload; no signed user actor. Validate that resourceId belongs to Experience. Append one immutable owner-local outcome per job/state and deduplicate acknowledgments; only UNKNOWN may reconcile to SENT or FAILED. This endpoint records outcomes only and never dispatches. Integration must use fresh API-127 channel/purpose eligibility before provider submission and owns escaped template rendering, provider attempt keys and UNKNOWN provider reconciliation. Unknown or repeated query/body fields are rejected; API-137 has no resource version/If-Match. Provider reference is validated but not persisted.','ExperienceNotificationAcknowledged',[parameter('Idempotency-Key','header',{'type':'string','minLength':1,'maxLength':100,'pattern':r'^[ -~]+$'},True)])
    op['security']=[{'workload':['notification.write']}]
    op['requestBody']={'required':True,'content':{'application/json':{'schema':REF('ExperienceNotificationDelivery')}}}
    op['responses']['200']['headers']={'Cache-Control':{'schema':{'const':'no-store'}}}
    spec['paths']['/internal/v1/notification-deliveries']={'post':op}
    return spec
