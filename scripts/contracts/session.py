"""Implemented BFF API-001–004 contracts, composed into commerce OpenAPI."""
def extend(spec):
    from authorization import UUID, REF, object_schema, parameter, operation
    null=lambda value: {'anyOf':[value,{'type':'null'}]}
    schemas=spec['components']['schemas']
    schemas['SessionDTO']=object_schema({'authenticated':{'type':'boolean'},'partyId':null(UUID),'displayName':null({'type':'string','maxLength':160}),'roles':{'type':'array','maxItems':8,'uniqueItems':True,'items':{'type':'string','enum':['CUSTOMER',*schemas['Role']['enum']]}},'organizationMemberships':{'type':'array','maxItems':100,'items':object_schema({'organizationId':UUID,'role':{'type':'string','enum':['OWNER','BUYER']}})},'expiresAt':null({'type':'string','format':'date-time'}),'csrfToken':null({'type':'string','pattern':'^[A-Za-z0-9_-]{43}$'})})
    def redirect(api,desc,params):
        op=operation(api,desc,'SessionDTO',params)
        op['security']=[]
        op['responses'].pop('200')
        op['responses']['302']={'description':'Redirect; no response body','headers':{'Location':{'schema':{'type':'string'},'description':'Configured identity target for login; allowlisted relative return path for callback'},'Set-Cookie':{'schema':{'type':'string'},'description':'Secure HttpOnly host-only SameSite=Lax session cookie; explicit local exception only'}}}
        op['responses']['429']={'description':'Shared pending-flow abuse budget exceeded','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}}
        return op
    login=redirect('API-001','No body. Creates encrypted pending state/nonce/PKCE S256 verifier with five-minute expiry. Only configured return paths allowed. Twenty unexpired pending flows per keyed socket-IP fingerprint and 10000 globally; shared owner transaction budget across replicas. Browser credentials are not accepted.',[parameter('returnTo','query',{'type':'string','maxLength':200,'default':'/account'})])
    callback=redirect('API-002','No body. Pending cookie plus exact one-use state required. Consume before confidential provider exchange; signature, audience, nonce, issuer, subject and PKCE verified. Provision signed issuer/subject identity; rotate cookie and redirect to stored allowlisted return path. Failed or uncertain exchange invalidates flow; never automatically retry code. Never expose provider tokens.',[parameter('code','query',{'type':'string','minLength':1,'maxLength':200},True),parameter('state','query',{'type':'string','pattern':'^[A-Za-z0-9_-]{43}$'},True)])
    view=operation('API-003','No query or body. Anonymous/expired/revoked sessions return authenticated=false with empty roles/memberships and null protected fields. Current commerce roles/membership resolve on every request. Refresh near-expiry tokens with CAS; uncertain refresh deletes local session without retry. ExpiresAt is current idle/absolute expiry. No provider tokens returned. Cache-Control:no-store.','SessionDTO',[]);view['security']=[]
    logout=operation('API-004','Exact empty JSON object and session-bound CSRF. Atomically audit and delete JDBC session before provider revocation HTTP. Always clear cookie after valid request. Provider failure returns 503 with local session already deleted; no fabricated provider success. No domain event.','SessionDTO',[parameter('X-CSRF-TOKEN','header',{'type':'string','pattern':'^[A-Za-z0-9_-]{43}$'},True)])
    logout['requestBody']={'required':True,'content':{'application/json':{'schema':{'type':'object','additionalProperties':False,'maxProperties':0}}}}
    logout['responses'].pop('200');logout['responses']['204']={'description':'Local deletion and supported provider revocation complete; no body'};logout['responses']['503']={'description':'Provider unavailable; local session already deleted','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}}
    spec['paths']['/bff/login']={'get':login};spec['paths']['/bff/callback']={'get':callback};spec['paths']['/bff/session']={'get':view};spec['paths']['/bff/logout']={'post':logout}
    return spec
