"""Bounded staff owner projections used by the admin portal."""
def extend(spec, owner):
    from authorization import object_schema, operation, parameter, REF, UUID, VERSION
    s=spec['components']['schemas'];text={'type':'string'};nullable=lambda x:{'anyOf':[x,{'type':'null'}]}
    if owner=='commerce':
        rows=[('StaffQuoteSummary','API-209','/api/v1/staff/quotes','SALES',{'id':UUID,'state':text,'version':VERSION,'createdAt':{'type':'string','format':'date-time'},'partyId':UUID,'customerName':text,'customerEmail':nullable(text),'revisionId':nullable(UUID),'approvalStatus':nullable(text),'terms':nullable(text),'total':nullable({'type':'number'}),'validUntil':nullable({'type':'string','format':'date-time'})}),('InventoryMovementSummary','API-210','/api/v1/staff/inventory-movements','OPERATIONS',{'id':UUID,'skuId':UUID,'locationId':UUID,'sku':text,'product':text,'change':{'type':'number'},'reservedChange':{'type':'number'},'allocatedChange':{'type':'number'},'reason':text,'date':{'type':'string','format':'date-time'},'by':text,'type':text})]
        rows.append(('StaffCustomerSummary','API-211','/api/v1/staff/customers','SALES',{'id':UUID,'name':text,'email':nullable(text),'phone':nullable(text),'joined':{'type':'string','format':'date-time'},'status':text,'orders':VERSION,'spend':{'type':'number'},'quotes':VERSION}))
    else:
        props={**s['ContentRevisionDTO']['properties'],'itemVersion':VERSION}
        rows=[('StaffContentSummary','API-208','/api/v1/staff/content','CONTENT_EDITOR',props)]
    for name,api,path,role,props in rows:
        s[name]=object_schema(props);s[name+'Page']=object_schema({'items':{'type':'array','maxItems':100,'items':REF(name)},'page':VERSION,'size':{'type':'integer','minimum':1,'maximum':100},'totalElements':VERSION,'totalPages':VERSION})
        op=operation(api,'Current '+role+'; bounded stable owner-local staff read. No mutation or event.',name+'Page',[parameter('page','query',{'type':'integer','minimum':0,'maximum':10000,'default':0}),parameter('size','query',{'type':'integer','minimum':1,'maximum':100,'default':100})])
        if owner=='experience':op['security']=[{'workload':['content.read_WITH_SIGNED_ACTOR']}]
        op['responses']['200']['headers']={'Cache-Control':{'schema':{'const':'no-store'}}}
        spec['paths'].setdefault(path,{})['get']=op
    return spec
