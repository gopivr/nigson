"""Reviewed BP1 market contracts. Draft creation confers no commercial activation."""
def extend(spec):
    from authorization import object_schema, parameter, operation, REF, UUID, VERSION
    s=spec['components']['schemas']
    tax=object_schema({'schemaVersion':{'const':1},'mode':{'enum':['NONE','EXCLUSIVE','INCLUSIVE']},'rate':{'type':'string','pattern':r'^(0(\.[0-9]{1,18})?|1(\.0{1,18})?)$'},'rounding':{'const':'HALF_UP'},'approvalReference':{'type':'string','minLength':1,'maxLength':120}})
    tax['allOf']=[{'if':{'properties':{'mode':{'const':'NONE'}}},'then':{'properties':{'rate':{'type':'string','pattern':r'^0(\.0{1,18})?$'}}}}]
    import json
    from pathlib import Path
    address=json.loads((Path(__file__).resolve().parents[2]/'contracts/rules/address-rules.schema.json').read_text());address.pop('$schema',None)
    s['TaxPolicy']=tax;s['AddressRules']=address
    s['MarketDestination']=object_schema({'countryCode':{'type':'string','pattern':'^[A-Z]{2}$'},'addressRules':REF('AddressRules'),'deliveryEnabled':{'type':'boolean'}})
    s['NewMarket']=object_schema({'code':{'type':'string','pattern':'^[A-Z][A-Z0-9_-]{1,31}$'},'name':{'type':'string','minLength':1,'maxLength':100},'currency':{'type':'string','pattern':'^[A-Z]{3}$'}})
    s['MarketDTO']=object_schema({'id':UUID,**s['NewMarket']['properties'],'enabled':{'type':'boolean'},'destinations':{'type':'array','maxItems':250,'items':REF('MarketDestination')},'taxPolicy':{'oneOf':[object_schema({}),REF('TaxPolicy')],'description':'Empty object only for a disabled draft; it is never an approved zero tax policy.'},'returnsPolicyRevision':{'type':['string','null'],'maxLength':80},'retailPriceBookId':{'anyOf':[UUID,{'type':'null'}]},'policyRevision':{'type':'integer','format':'int64','minimum':1},'version':VERSION})
    s['PublicMarket']=object_schema({'id':UUID,'code':{'type':'string'},'currency':{'type':'string'},'destinations':{'type':'array','uniqueItems':True,'maxItems':250,'items':{'type':'string','pattern':'^[A-Z]{2}$'}},'policyRevision':{'type':'integer','minimum':1}})
    s['PublicMarkets']={'type':'array','maxItems':100,'items':REF('PublicMarket')}
    reason={'type':'string','minLength':10,'maxLength':300}
    s['MarketBookSelection']=object_schema({'priceBookId':UUID,'reason':reason});s['MarketEligibility']=object_schema({'eligible':{'type':'boolean'},'reason':reason});s['ProductMarketDTO']=object_schema({'productId':UUID,'marketId':UUID,'eligible':{'type':'boolean'},'version':VERSION})
    headers=[parameter('Idempotency-Key','header',{'type':'string','minLength':1,'maxLength':100},True),parameter('X-CSRF-TOKEN','header',{'type':'string','minLength':43,'maxLength':43},True)]
    match=parameter('If-Match','header',{'type':'string','pattern':'^"(0|[1-9][0-9]{0,18})"$'},True)
    read=operation('API-029','Public bounded replica rate budget and repeatable-read list. Only enabled NG/NGN2 and enabled NG destinations; international policy is unavailable until BP5. No query/body, browser bearer or actor proof. No-store prevents stale enabled policy.','PublicMarkets',[]);read['security']=[]
    create=operation('API-169','Current FINANCE/no organization/recent MFA15m and CSRF. Supported active ISO currency must already exist; NGN scale2 immutable. Creates disabled market, policyRevision1/version0, taxPolicy{}, empty destinations/null returns and book. No approval, price or destination guessed. Atomic audit/idempotency; current role/MFA before replay. NGN-001 owns approved currency seed.','MarketDTO',headers)
    book=operation('API-162','Current FINANCE/recent MFA/CSRF/If-Match. Require active RETAIL book of matching approved currency and current effective interval. Lock Party/market/currency/book; atomically increment market version/policy revision with EVT-008/audit/response. Assignment never enables a market.','MarketDTO',[parameter('id','path',UUID,True),*headers,match])
    eligibility=operation('API-031','Current CONTENT_PUBLISHER/CSRF/If-Match. Product aggregate version governs first and subsequent restrictions. Missing eligibility denies selling. Shared commerce transaction locks Party/product/market, stores explicit restriction and reason, increments product version/public revision and emits EVT-006 with current eligible market codes. Current authority precedes cached replay.','ProductMarketDTO',[parameter('id','path',UUID,True),parameter('marketId','path',UUID,True),*headers,match])
    for op,request in ((create,'NewMarket'),(book,'MarketBookSelection'),(eligibility,'MarketEligibility')):
        op['requestBody']={'required':True,'content':{'application/json':{'schema':REF(request)}}}
        op['responses']['200']['headers']={'ETag':{'schema':{'type':'string'}},'Cache-Control':{'schema':{'const':'no-store'}}}
    create['responses']['201']=create['responses'].pop('200')
    read['responses']['200']['headers']={'Cache-Control':{'schema':{'const':'no-store'}}};read['responses']['429']={'description':'Bounded replica read budget exceeded'}
    for op in (read,create,book,eligibility):op['responses']['422']['description']='Market rule violation or disabled policy; no commercial activation from draft inputs'
    spec['paths']['/api/v1/markets']={'get':read};spec['paths']['/api/v1/staff/markets']={'post':create};spec['paths']['/api/v1/staff/markets/{id}/retail-price-book']={'put':book};spec['paths']['/api/v1/staff/products/{id}/markets/{marketId}']={'put':eligibility};return spec
