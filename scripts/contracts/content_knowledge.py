"""EXP-CONTE-002 API-125/126 private approved Experience knowledge contracts."""

def extend(spec):
    from authorization import object_schema, operation, parameter, REF, UUID
    schemas=spec['components']['schemas']
    schemas['ContentKnowledgeRevisionRequest']=object_schema({'contentItemId':UUID,'revision':{'type':'integer','minimum':1}})
    schemas['ContentKnowledgeValidationInput']=object_schema({'items':{'type':'array','minItems':1,'maxItems':20,'items':REF('ContentKnowledgeRevisionRequest')}})
    schemas['ContentKnowledgeRevisionStatus']=object_schema({'contentItemId':UUID,'revision':{'type':'integer','minimum':1},'approved':{'type':'boolean'},'current':{'type':'boolean'}})
    schemas['ContentKnowledgeValidationResult']=object_schema({'items':{'type':'array','minItems':1,'maxItems':20,'items':REF('ContentKnowledgeRevisionStatus')}})
    url={'type':'string','format':'uri','pattern':'^https://'}
    nullable=lambda value:{'anyOf':[value,{'type':'null'}]}
    schemas['ContentBody']=object_schema({'schemaVersion':{'const':1},'blocks':{'type':'array','maxItems':200,'items':{'oneOf':[
        object_schema({'type':{'const':'TEXT'},'text':{'type':'string','maxLength':20000}}),
        object_schema({'type':{'const':'IMAGE'},'assetId':UUID,'alt':{'type':'string','maxLength':300}}),
        object_schema({'type':{'const':'VIDEO'},'provider':{'enum':['YOUTUBE','VIMEO']},'videoId':{'type':'string','maxLength':100},'caption':{'type':'string','maxLength':500}}),
        object_schema({'type':{'const':'PERSON'},'name':{'type':'string','maxLength':160},'title':{'type':'string','maxLength':160},'bio':{'type':'string','maxLength':10000},'assetId':nullable(UUID)}),
        object_schema({'type':{'const':'LINK'},'label':{'type':'string','maxLength':160},'url':url}),
        object_schema({'type':{'const':'MAP'},'channelKey':{'type':'string','maxLength':80}}),
        object_schema({'type':{'const':'PRODUCT_LIST'},'productIds':{'type':'array','maxItems':30,'items':UUID}}),
        object_schema({'type':{'const':'FAQ'},'question':{'type':'string','maxLength':500},'answer':{'type':'string','maxLength':5000}})]}}})
    schemas['SeoDTO']=object_schema({'title':{'type':'string','maxLength':70},'description':{'type':'string','maxLength':180},'canonicalUrl':nullable(url),'robots':{'enum':['INDEX','NOINDEX']},'alternates':{'type':'array','maxItems':30,'items':object_schema({'locale':{'type':'string','maxLength':16},'url':url})},'structuredData':{'type':'object','maxProperties':50,'additionalProperties':True}})
    schemas['ContentKnowledgeDTO']=object_schema({'id':UUID,'type':{'type':'string','enum':['HOME','ABOUT','LEADERSHIP','DIVISION','BLOG','VIDEO','FAQ','CAREERS','CONTACT']},'slug':{'type':'string','maxLength':220},'locale':{'type':'string','maxLength':16},'revision':{'type':'integer','minimum':1},'title':{'type':'string','maxLength':220},'body':REF('ContentBody'),'metadata':REF('SeoDTO'),'publishedAt':{'type':'string','format':'date-time'},'approved':{'const':True},'checksum':{'type':'string','pattern':'^[a-f0-9]{64}$'},'visibility':{'const':'PUBLIC'}})
    scopes=spec['components']['securitySchemes']['workload']['flows']['clientCredentials']['scopes']
    scopes.update({'knowledge.validate':'Validate approval and current publication state for up to 20 Experience content revisions','knowledge.read':'Read the current approved PUBLIC Experience content revision'})
    spec['components']['securitySchemes']['workload']['description']='Private ingress. The route registry requires exact nigson-ai client and Experience audience; these APIs accept no signed actor or browser credential.'
    validate=operation('API-125','Exact nigson-ai workload client with knowledge.validate. Read-only, owner-local validation for at most 20 requested revisions. Missing or unavailable revisions return approved=false/current=false; stale, draft, preview, archived, future-scheduled and withdrawn revisions are never current. No actor assertion, mutation, or direct event.','ContentKnowledgeValidationResult',[])
    validate['security']=[{'workload':['knowledge.validate']}]
    validate['requestBody']={'required':True,'content':{'application/json':{'schema':REF('ContentKnowledgeValidationInput')}}}
    validate['responses']['200']['headers']={'Cache-Control':{'schema':{'const':'no-store'}}}
    read=operation('API-126','Exact nigson-ai workload client with knowledge.read. Returns only the active non-deleted content item current pointer when it identifies an approved, published, checksum-valid PUBLIC revision; otherwise returns safe NOT_FOUND. Leads and applications are excluded. No actor assertion, mutation, or direct event.','ContentKnowledgeDTO',[parameter('id','path',UUID,True)])
    read['security']=[{'workload':['knowledge.read']}]
    read['responses']['200']['headers']={'Cache-Control':{'schema':{'const':'no-store'}}}
    spec['paths']['/internal/v1/content/revision-validation']={'post':validate}
    spec['paths']['/internal/v1/content/knowledge/{id}']={'get':read}
    return spec
