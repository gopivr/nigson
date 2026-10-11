"""Exact owned BP1 cart API contracts."""
def extend(spec):
    from authorization import object_schema, parameter, operation, REF, UUID, VERSION
    s=spec['components']['schemas']
    qty={'type':'string','pattern':r'^[0-9]{1,17}(\.[0-9]{1,3})?$','description':'Positive; current SKU scale, pack and wholesale quantity rules apply. Zero requires DELETE.'}
    s['NewCart']=object_schema({'marketId':UUID,'organizationId':UUID});s['NewCart']['required'].remove('organizationId')
    s['CartQuantity']=object_schema({'quantity':qty})
    s['CartLineDTO']=object_schema({'id':UUID,'skuId':UUID,'quantity':qty})
    s['CartDTO']=object_schema({'id':UUID,'marketId':UUID,'organizationId':{'anyOf':[UUID,{'type':'null'}]},'state':{'type':'string','enum':['ACTIVE','CHECKOUT_PENDING','CONVERTED','EXPIRED']},'lines':{'type':'array','maxItems':100,'items':REF('CartLineDTO')},'version':VERSION,'lastActivityAt':{'type':'string','format':'date-time'}})
    key=parameter('Idempotency-Key','header',{'type':'string','minLength':1,'maxLength':100},True)
    csrf=parameter('X-CSRF-TOKEN','header',{'type':'string','minLength':43,'maxLength':43},True)
    match=parameter('If-Match','header',{'type':'string','pattern':'^"(0|[1-9][0-9]{0,18})"$'},True)
    path=parameter('id','path',UUID,True);sku=parameter('skuId','path',UUID,True)
    create=operation('API-032','Current CUSTOMER and exact organization entitlement before replay. Create or return unique ACTIVE party/organization/market context, with nil business UUIDs prohibited. Does not change existing activity/version. Durable command/audit; no stock reservation.','CartDTO',[key,csrf])
    get=operation('API-033','Current CUSTOMER, owner and organization entitlement. Repeatable read of current cart; no shared cache or activity mutation.','CartDTO',[path])
    put=operation('API-034','Current CUSTOMER and owned ACTIVE cart. CSRF/key/If-Match; positive quantity, current eligible SKU/price/pack and wholesale quantity checks. Aggregate version and activity increment atomically with line and audit. No stock promise, provider call or whole-order minimum check before checkout.','CartDTO',[path,sku,key,csrf,match])
    delete=operation('API-035','Current CUSTOMER and owned ACTIVE cart. CSRF/key/If-Match; remove existing line and increment aggregate version/activity atomically. Removal remains possible when the SKU is no longer saleable. Durable replay returns original acknowledgment/version.','CartDTO',[path,sku,key,csrf,match])
    for op,body in ((create,'NewCart'),(put,'CartQuantity')):op['requestBody']={'required':True,'content':{'application/json':{'schema':REF(body)}}}
    create['responses']['201']=create['responses'].pop('200')
    delete['responses'].pop('200');delete['responses']['204']={'description':'Line removed; empty body'}
    for op,success in ((create,'201'),(get,'200'),(put,'200'),(delete,'204')):
        op['responses'][success]['headers']={'Cache-Control':{'schema':{'const':'no-store'}},'ETag':{'schema':{'type':'string'}}}
        op['responses']['422']['description']='CART_RULE_VIOLATION or disabled organization phase'
    spec['paths']['/api/v1/carts']={'post':create}
    spec['paths']['/api/v1/carts/{id}']={'get':get}
    spec['paths']['/api/v1/carts/{id}/lines/{skuId}']={'put':put,'delete':delete}
    return spec
