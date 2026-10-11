"""Reviewed exact BP1 price/book and immutable checkout quote contracts."""
def extend(spec):
    from authorization import object_schema, parameter, operation, REF, UUID, VERSION
    s=spec['components']['schemas']
    money={'type':'string','pattern':r'^[0-9]{1,15}(\.[0-9]{1,4})?$'}
    quantity={'type':'string','pattern':r'^[0-9]{1,17}(\.[0-9]{1,3})?$'}
    instant={'type':'string','format':'date-time'}
    currency={'type':'string','pattern':'^[A-Z]{3}$'}
    segment={'type':'string','enum':['RETAIL','DISTRIBUTOR']}
    s['PriceEntry']=object_schema({'skuId':UUID,'minimumQuantity':quantity,'unitAmount':money})
    entries={'type':'array','minItems':1,'maxItems':500,'items':REF('PriceEntry')}
    properties={'currency':currency,'segment':segment,'effectiveFrom':instant,'effectiveUntil':instant,'entries':entries}
    revision=object_schema(properties);revision['required'].remove('effectiveUntil');s['PriceRevision']=revision
    new=object_schema({'code':{'type':'string','minLength':1,'maxLength':60},**properties});new['required'].remove('effectiveUntil');s['NewPriceBook']=new
    s['PriceBookDTO']=object_schema({'id':UUID,'code':new['properties']['code'],'revision':{'type':'integer','minimum':1,'format':'int64'},**properties,'effectiveUntil':{'anyOf':[instant,{'type':'null'}]},'active':{'type':'boolean'},'version':VERSION})
    s['PriceBooksPage']=object_schema({'items':{'type':'array','maxItems':100,'items':REF('PriceBookDTO')},'page':{'type':'integer','minimum':0},'size':{'type':'integer','minimum':1,'maximum':100},'totalElements':VERSION,'totalPages':VERSION})
    s['PriceDTO']=object_schema({'skuId':UUID,'unitAmount':money,'currency':currency,'minimumQuantity':quantity,'packMultiple':quantity,'priceBookRevision':{'type':'integer','minimum':1,'format':'int64'},'validUntil':instant})
    s['PriceActivation']=object_schema({'reason':{'type':'string','minLength':10,'maxLength':300}})
    quote=object_schema({'cartId':UUID,'addressId':UUID,'carrierQuoteId':UUID,'couponCode':{'type':'string','pattern':'^[A-Za-z0-9_-]{1,80}$'}});quote['required'].remove('couponCode');s['NewCheckoutQuote']=quote
    s['CheckoutQuoteLine']=object_schema({'skuId':UUID,'quantity':quantity,'unitAmount':money,'lineTotal':money})
    s['CheckoutQuoteTotals']=object_schema({key:money for key in ('subtotal','discount','tax','delivery','grandTotal')})
    s['CheckoutQuote']=object_schema({'id':UUID,'cartVersion':VERSION,'currency':currency,'lines':{'type':'array','minItems':1,'maxItems':100,'items':REF('CheckoutQuoteLine')},'totals':REF('CheckoutQuoteTotals'),'quoteHash':{'type':'string','pattern':'^[a-f0-9]{64}$'},'expiresAt':instant})
    headers=[parameter('Idempotency-Key','header',{'type':'string','minLength':1,'maxLength':100},True),parameter('X-CSRF-TOKEN','header',{'type':'string','minLength':43,'maxLength':43},True)]
    match=parameter('If-Match','header',{'type':'string','pattern':'^"(0|[1-9][0-9]{0,18})"$'},True)
    path=parameter('id','path',UUID,True)
    read=operation('API-026','Current approved NG/NGN2 retail price from the explicitly selected effective active book. Greatest qualifying tier; exact quantity scale/pack and minor units. Public retail only; organization requires current authenticated membership and the explicitly configured distributor buying entitlement at BP3. An invalid assignment never falls back. Bounded replica budget and repeatable-read/no-store.','PriceDTO',[path,parameter('market','query',{'type':'string','pattern':'^[A-Z][A-Z0-9_-]{1,31}$'},True),parameter('quantity','query',quantity,True),parameter('organizationId','query',UUID)])
    read['security']=[{}, {'bffSession':[]}]
    read['responses']['429']={'description':'Replica price read budget exceeded'}
    listing=operation('API-150','Current SALES, no organization. Bounded repeatable-read staff page of draft and active revisions; fixed sort plus UUID tie-break. Entries loaded in one bounded batch.','PriceBooksPage',[parameter('segment','query',segment),parameter('currency','query',currency),parameter('page','query',{'type':'integer','minimum':0,'default':0}),parameter('size','query',{'type':'integer','minimum':1,'maximum':100,'default':20}),parameter('sort','query',{'type':'string','enum':['code,asc','code,desc','revision,asc','revision,desc','effectiveFrom,asc','effectiveFrom,desc'],'default':'code,asc'})])
    create=operation('API-027','Current SALES/CSRF/key. Explicit nonempty draft revision1; active approved currency, SKU scale and exact minor units. Numeric duplicate thresholds rejected; family advisory lock plus durable idempotency and atomic audit. No prices or market activation invented.','PriceBookDTO',headers)
    activate=operation('API-028','Current FINANCE/recent MFA15m/CSRF/key/If-Match. Effective nonexpired valid draft and nonoverlapping active family interval. Immutable published entries; atomically activate/version/audit/EVT-007/ack. Does not choose a default market book; API-162 remains explicit.','PriceBookDTO',[path,*headers,match])
    revise=operation('API-151','Current SALES/CSRF/key/If-Match of parent. Serialize family, require latest revision and next effective interval, same currency/segment. Copy-on-write new draft/revision; published prices never changed. An open-ended active parent cannot be silently shortened.','PriceBookDTO',[path,*headers,match])
    checkout=operation('API-036','Current CUSTOMER/CSRF/key with owned cart/address. No reservation/provider HTTP. Lock current cart, product/SKU, owned policies and carrier binding; require unexpired QUOTED current cart/version/address/package/service/currency/mode and reviewed provider configuration. Canonical immutable snapshot/hash freezes all sources, fee and expiry up to5m. Approved offer takes precedence; current membership/eligibility and quantity still checked. BP1 coupon or a higher-phase enabled campaign fails closed until promotion port is implemented. Same-key replay returns the original snapshot and expiry.','CheckoutQuote',headers)
    for op,request in ((create,'NewPriceBook'),(activate,'PriceActivation'),(revise,'PriceRevision'),(checkout,'NewCheckoutQuote')):
        op['requestBody']={'required':True,'content':{'application/json':{'schema':REF(request)}}}
        op['responses']['201']=op['responses'].pop('200')
    for op in (read,listing,create,activate,revise,checkout):
        success='201' if '201' in op['responses'] else '200'
        op['responses'][success]['headers']={'Cache-Control':{'schema':{'const':'no-store'}}}
        if op in (create,activate,revise):op['responses'][success]['headers']['ETag']={'schema':{'type':'string'}}
        op['responses']['422']['description']='Pricing rule violation, missing approval or disabled phase feature'
    spec['paths']['/api/v1/catalogue/skus/{id}/price']={'get':read}
    spec['paths']['/api/v1/staff/price-books']={'get':listing,'post':create}
    spec['paths']['/api/v1/staff/price-books/{id}/activation']={'post':activate}
    spec['paths']['/api/v1/staff/price-books/{id}/revisions']={'post':revise}
    spec['paths']['/api/v1/checkout/quotes']={'post':checkout}
    return spec
