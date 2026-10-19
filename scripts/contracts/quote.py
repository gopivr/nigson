"""Commerce-owned immutable negotiated quote contracts."""
def extend(spec):
    from authorization import object_schema, parameter, operation, REF, UUID, VERSION
    s=spec['components']['schemas'];instant={'type':'string','format':'date-time'};money={'type':'string','pattern':r'^[0-9]{1,15}\.[0-9]{2}$'};quantity={'type':'string','pattern':r'^[0-9]{1,17}(\.[0-9]{1,3})?$'};text=lambda maximum:{'type':'string','minLength':1,'maxLength':maximum};nullable=lambda x:{'anyOf':[x,{'type':'null'}]}
    s['QuoteLineInput']=object_schema({'skuId':UUID,'quantity':quantity,'agreedUnitAmount':money,'discountAmount':money,'taxAmount':money})
    lines={'type':'array','minItems':1,'maxItems':100,'items':REF('QuoteLineInput')}
    s['NewQuote']=object_schema({'partyId':UUID,'organizationId':UUID,'marketId':UUID,'sourceLeadId':UUID,'validUntil':instant,'terms':text(4000),'lines':lines});s['NewQuote']['required'].remove('organizationId')
    s['NewQuoteRevision']=object_schema({'validUntil':instant,'terms':text(4000),'lines':lines})
    s['QuoteOffer']=object_schema({'revisionId':UUID,'reason':text(300)})
    s['QuoteApproval']=object_schema({'approve':{'type':'boolean'},'reason':text(300)})
    s['QuoteAcceptInput']=object_schema({'revisionId':UUID,'addressId':UUID,'carrierQuoteId':UUID})
    s['QuoteLineDTO']=object_schema({'id':UUID,'skuId':UUID,'quantity':quantity,'unitAmount':money,'discountAmount':money,'taxAmount':money,'lineTotal':money})
    s['QuoteDTO']=object_schema({'id':UUID,'state':{'enum':['DRAFT','OFFERED','ACCEPTED','REJECTED','EXPIRED','CANCELLED']},'revisionId':UUID,'revision':{'type':'integer','minimum':1},'total':money,'currency':{'const':'NGN'},'validUntil':instant,'lines':{'type':'array','minItems':1,'maxItems':100,'items':REF('QuoteLineDTO')},'version':VERSION})
    key=parameter('Idempotency-Key','header',{'type':'string','minLength':1,'maxLength':100},True);csrf=parameter('X-CSRF-TOKEN','header',{'type':'string','minLength':43,'maxLength':43},True);match=parameter('If-Match','header',{'type':'string','pattern':'^"(0|[1-9][0-9]{0,18})"$'},True);path=parameter('id','path',UUID,True);revision=parameter('revisionId','path',UUID,True)
    defs=[('/api/v1/staff/quotes','post','API-068','NewQuote','QuoteDTO',201,[key,csrf],'Current SALES; verify the source lead with Experience before any write, exact target party/organization match, NG/NGN and currently eligible SKUs. Create quote and immutable revision 1 as DRAFT; compute total server-side. Lead association is an acknowledged private command after commit and retries use the original idempotency key.'),('/api/v1/staff/quotes/{id}/revisions','post','API-069','NewQuoteRevision','QuoteDTO',201,[path,key,csrf,match],'Current SALES/CSRF/key/If-Match. Append a new immutable DRAFT revision; never modify an offered or approved revision. Aggregate version increments exactly once.'),('/api/v1/staff/quotes/{id}/offer','post','API-070','QuoteOffer','QuoteDTO',200,[path,key,csrf,match],'Current SALES/CSRF/key/If-Match. Only the current FINANCE-approved, nonexpired revision can be offered; successful offer emits EVT-013 in the Commerce transaction and awaits acknowledged Experience lead association after commit.'),('/api/v1/quotes/{id}','get','API-071',None,'QuoteDTO',200,[path],'Current CUSTOMER; exact quote party or active authorized purchasing organization. No cross-owner read.'),('/api/v1/quotes/{id}/accept','post','API-072','QuoteAcceptInput','CheckoutAttemptDTO',201,[path,key,csrf,match],'Current CUSTOMER/CSRF/key/If-Match. Accept only the current offered approved nonexpired revision. Require matching source_quote_revision_id carrier quote; create checkout quote and order through the local Commerce checkout port, preserve frozen terms, revalidate market/stock, and atomically commit accepted order plus EVT-014 and EVT-001. Initialize payment only after commit. Lead association is retried with the same durable command identity.'),('/api/v1/staff/quotes/{id}/revisions/{revisionId}/approval','post','API-173','QuoteApproval','QuoteDTO',200,[path,revision,key,csrf,match],'Current FINANCE with recent MFA; CSRF/key/If-Match. Approver must differ from the revision creator. Record APPROVED or REJECTED separately; an offer requires APPROVED.')]
    for route,method,api,body,response,status,params,description in defs:
        op=operation(api,description,response,params)
        if body:op['requestBody']={'required':True,'content':{'application/json':{'schema':REF(body)}}}
        if status!=200:op['responses'][str(status)]=op['responses'].pop('200')
        op['responses'][str(status)]['headers']={'Cache-Control':{'schema':{'const':'no-store'}}}
        if api!='API-072':op['responses'][str(status)]['headers']['ETag']={'schema':{'type':'string'}}
        op['responses']['412']={'description':'Stale aggregate version','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}}
        op['responses']['422']={'description':'QUOTE_RULE_VIOLATION or disabled feature','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}}
        if api in ('API-068','API-070','API-072'):op['responses']['503']={'description':'Experience lead acknowledgement unavailable or provider outcome uncertain; retry the same command identity','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}}
        spec['paths'][route]={method:op}
    eligibility={'QuoteSkuEligibilityItem':object_schema({'skuId':UUID,'quantity':quantity}),
                 'QuoteSkuEligibilityRequest':object_schema({'partyId':UUID,'marketId':UUID,'organizationId':{'anyOf':[UUID,{'type':'null'}]},'items':{'type':'array','minItems':1,'maxItems':50,'items':REF('QuoteSkuEligibilityItem')}}),
                 'QuoteSkuEligibilityResult':object_schema({'items':{'type':'array','minItems':1,'maxItems':50,'items':object_schema({'skuId':UUID,'quantityScale':{'type':'integer','minimum':0,'maximum':3}})}})}
    s.update(eligibility)
    op=operation('API-206','Exact nigson-experience workload. Verify current Commerce market, active organization membership/entitlement when supplied, and active market-eligible SKUs; return only IDs and quantity scales. No cross-owner query or quote mutation.', 'QuoteSkuEligibilityResult',[])
    op['security']=[{'workload':['catalogue.quote-eligibility.read']}]
    op['requestBody']={'required':True,'content':{'application/json':{'schema':REF('QuoteSkuEligibilityRequest')}}}
    op['responses']['200']['headers']={'Cache-Control':{'schema':{'const':'no-store'}}}
    op['responses']['422']={'description':'Market or requested SKU is not eligible','content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}}
    spec['paths']['/internal/v1/quote-sku-eligibility']={'post':op}
    spec['components']['securitySchemes']['quoteCatalogueWorkload']={'type':'oauth2','flows':{'clientCredentials':{'tokenUrl':'/realms/nigson/protocol/openid-connect/token','scopes':{'catalogue.quote-eligibility.read':'Validate Experience quote request market, organization and SKU eligibility'}}},'description':'Private exact nigson-experience workload client and Commerce audience; no browser actor.'}
    op['security']=[{'quoteCatalogueWorkload':['catalogue.quote-eligibility.read']}]
    return spec
