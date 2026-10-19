"""Generated Commerce API-075 reporting contract."""
REF=lambda name:{'$ref':'#/components/schemas/'+name}
UUID={'type':'string','format':'uuid','pattern':r'^[a-fA-F0-9]{8}(-[a-fA-F0-9]{4}){3}-[a-fA-F0-9]{12}$'}
def obj(properties): return {'type':'object','additionalProperties':False,'required':list(properties),'properties':properties}
def extend(spec):
    schemas=spec['components']['schemas']
    schemas['ReportMoney']=obj({'amount':{'type':'string','pattern':r'^(0|[1-9][0-9]{0,14})(\.[0-9]{1,4})?$'},'currency':{'type':'string','pattern':'^[A-Z]{3}$'}})
    schemas['ReportPeriod']=obj({'from':{'type':'string','format':'date-time'},'to':{'type':'string','format':'date-time'}})
    schemas['ReportProduct']=obj({'skuId':UUID,'quantity':{'type':'string','pattern':r'^(0|[1-9][0-9]{0,16})(\.[0-9]{1,3})?$'},'revenue':REF('ReportMoney')})
    schemas['ReportStock']=REF('StockDTO')
    schemas['CommerceReport']=obj({'period':REF('ReportPeriod'),'currency':{'type':'string','pattern':'^[A-Z]{3}$'},'sales':REF('ReportMoney'),'orders':{'type':'integer','format':'int64','minimum':0},'customers':{'type':'integer','format':'int64','minimum':0},'products':{'type':'array','maxItems':100,'items':REF('ReportProduct')},'stock':{'type':'array','maxItems':100,'items':REF('ReportStock')}})
    query=lambda name,required,schema:{'name':name,'in':'query','required':required,'schema':schema}
    op={'operationId':'API-075','description':'ANALYST only; rechecks current Commerce role in the read snapshot. Sanitized Commerce-owned aggregates; never aggregates currencies. from is inclusive and to exclusive UTC Instants; period must be positive and at most 366 days. Settled orders and successful refunds determine sales. Optional organizationId scopes sales only after active Commerce organization validation; a scoped actor can request only its own organization. Product and stock arrays fail explicitly above 100 rows rather than truncate. Reject unknown/repeated parameters and request bodies. Response is no-store. Successful reads append a minimal Commerce audit record and query metric; no cross-owner reads, outbox events, or retention policy are introduced.','security':[{'bffSession':[]}],
        'parameters':[query('from',True,{'type':'string','format':'date-time'}),query('to',True,{'type':'string','format':'date-time'}),query('currency',True,{'type':'string','pattern':'^[A-Z]{3}$'}),query('organizationId',False,UUID)],
        'responses':{'200':{'description':'Sanitized Commerce report','headers':{'Cache-Control':{'schema':{'const':'no-store'}}},'content':{'application/json':{'schema':REF('CommerceReport')}}},**{str(code):{'description':description,'content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}} for code,description in [(400,'Invalid input'),(401,'Authentication required'),(403,'Access denied'),(404,'Organization not found'),(422,'Reporting limits exceeded'),(500,'Safe internal error')]}}
    }
    spec['paths']['/api/v1/staff/reports/commerce']={'get':op}
    return spec
