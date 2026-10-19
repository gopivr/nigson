"""Closed API-013–025/149 catalogue wire contracts and cache/version behavior."""

def extend(spec):
    from authorization import object_schema, parameter, operation, REF, UUID, VERSION
    spec['info']['title'] = 'Nigson commerce implemented contracts'
    schemas = spec['components']['schemas']
    text = lambda maximum: {'type': 'string', 'maxLength': maximum}
    nullable = lambda value: {'anyOf': [value, {'type': 'null'}]}
    array = lambda value: {'type': 'array', 'maxItems': 100, 'items': value}
    quantity = {'type': 'string', 'pattern': '^[0-9]{1,17}(\\.[0-9]{1,3})?$'}
    url = {'type': 'string', 'format': 'uri', 'pattern': '^https://'}
    schemas['CatalogueReference'] = object_schema({'id': UUID, 'name': text(120)})
    schemas['CatalogueSku'] = object_schema({'id': UUID, 'code': text(80), 'variantName': text(160), 'unit': text(20), 'packSize': quantity, 'available': {'type': 'boolean'}})
    schemas['CatalogueAsset'] = object_schema({'id': UUID, 'purpose': {'enum': ['IMAGE', 'BROCHURE']}, 'url': url})
    schemas['CatalogueAdminAsset'] = object_schema({'id': UUID, 'state': {'enum': ['UPLOADING', 'QUARANTINED', 'CLEAN', 'PUBLISHED', 'REJECTED']}, 'purpose': {'enum': ['IMAGE', 'BROCHURE']}, 'mimeType': text(100), 'size': {'type': 'integer', 'format': 'int64', 'minimum': 1}, 'checksum': {'type': 'string', 'pattern': '^[a-f0-9]{64}$'}, 'url': nullable(url), 'version': VERSION})
    product = {'id': UUID, 'name': text(200), 'slug': text(220), 'description': text(20000), 'specifications': {'type': 'object', 'maxProperties': 50, 'description': 'Registered bounded typed specification keys only; no executable HTML.'}, 'brand': nullable(REF('CatalogueReference')), 'categories': array(REF('CatalogueReference')), 'skus': array(REF('CatalogueSku')), 'assets': array(REF('CatalogueAsset')), 'revision': {'type': 'integer', 'format': 'int64', 'minimum': 1}}
    schemas['ProductDTO'] = object_schema(product)
    schemas['ProductAdminDTO'] = object_schema({**product, 'assets': array(REF('CatalogueAdminAsset')), 'status': {'enum': ['DRAFT', 'ACTIVE', 'ARCHIVED']}, 'version': VERSION})
    schemas['ProductSummaryDTO'] = object_schema({'id': UUID, 'name': text(200), 'slug': text(220), 'brandName': nullable(text(120)), 'imageUrl': nullable(url), 'available': {'type': 'boolean'}, 'revision': {'type': 'integer', 'format': 'int64', 'minimum': 1}})
    schemas['SkuAdminDTO'] = object_schema({'id': UUID, 'code': text(80), 'variantName': text(160), 'unit': text(20), 'quantityScale': {'type': 'integer', 'minimum': 0, 'maximum': 3}, 'packSize': quantity, 'version': VERSION})
    schemas['TaxonomyDTO'] = object_schema({'id': UUID, 'name': text(120), 'slug': text(140), 'parentId': nullable(UUID), 'active': {'type': 'boolean'}, 'version': VERSION})
    schemas['PublicCategoryDTO'] = object_schema({'id': UUID, 'parentId': nullable(UUID), 'name': text(120), 'slug': text(140), 'sortOrder': {'type': 'integer'}})
    schemas['PublicBrandDTO'] = object_schema({'id': UUID, 'name': text(120), 'slug': text(140)})
    schemas['RecommendationDTO'] = object_schema({'relatedProductId': UUID, 'kind': {'type': 'string', 'enum': ['RELATED', 'FEATURED']}, 'priority': {'type': 'integer', 'format': 'int32'}})
    schemas['RecommendationResultDTO'] = object_schema({'productId': UUID, 'items': {'type': 'array', 'maxItems': 100, 'items': REF('RecommendationDTO')}, 'version': VERSION})
    schemas['PublicRecommendationDTO'] = object_schema({'productId': UUID, 'name': text(200), 'reason': {'type': 'string', 'enum': ['RELATED', 'FEATURED', 'POPULAR']}})
    schemas['CatalogueKnowledgeDTO'] = object_schema({'id': UUID, 'revision': {'type': 'integer', 'format': 'int64', 'minimum': 1}, 'approved': {'type': 'boolean'}, 'marketCodes': {'type': 'array', 'maxItems': 100, 'items': {'type': 'string', 'pattern': '^[A-Z][A-Z0-9_-]{1,31}$'}}, 'name': text(200), 'description': text(20000), 'specifications': product['specifications'], 'publicUrl': url, 'checksum': {'type': 'string', 'pattern': '^[a-f0-9]{64}$'}})
    for prefix, item in [('ProductSummary', 'ProductSummaryDTO'), ('ProductAdmin', 'ProductAdminDTO')]:
        schemas[prefix+'Page'] = object_schema({'items': array(REF(item)), 'page': {'type': 'integer', 'minimum': 0}, 'size': {'type': 'integer', 'minimum': 1, 'maximum': 100}, 'totalElements': VERSION, 'totalPages': VERSION})
    create = {'name': {**text(200), 'minLength': 1}, 'slug': {**text(220), 'pattern': '^[a-z0-9]+(-[a-z0-9]+)*$'}, 'description': text(20000), 'brandId': nullable(UUID), 'divisionCode': nullable(text(40)), 'specifications': product['specifications'], 'categoryIds': {**array(UUID), 'uniqueItems': True}}
    schemas['ProductCreate'] = {**object_schema(create), 'required': ['name', 'slug', 'description', 'specifications', 'categoryIds']}
    schemas['ProductPatch'] = {**object_schema(create), 'required': [], 'minProperties': 1}
    schemas['PublicationCommand'] = object_schema({'action': {'enum': ['PUBLISH', 'ARCHIVE']}, 'reason': {'type': 'string', 'minLength': 10, 'maxLength': 500}})
    carrier = {'$ref': '../rules/carrier-packaging.schema.json'}
    sku = {'code': {**text(80), 'minLength': 1}, 'variantName': {**text(160), 'minLength': 1}, 'unit': {**text(20), 'minLength': 1}, 'quantityScale': {'type': 'integer', 'minimum': 0, 'maximum': 3}, 'packSize': quantity, 'weightKg': {'type': 'string', 'pattern': '^[0-9]{1,9}(\\.[0-9]{1,3})?$'}, 'carrierPackaging': carrier}
    schemas['SkuCreate'] = {**object_schema(sku), 'required': [name for name in sku if name != 'weightKg']}
    schemas['SkuPatch'] = {**object_schema({'variantName': sku['variantName'], 'active': {'type': 'boolean'}, 'weightKg': sku['weightKg'], 'carrierPackaging': carrier}), 'required': ['carrierPackaging']}
    for kind in ['Brand', 'Category']:
        fields = {'name': {**text(120), 'minLength': 1}, 'slug': {**text(140), 'pattern': '^[a-z0-9]+(-[a-z0-9]+)*$'}, 'active': {'type': 'boolean'}}
        if kind == 'Category': fields.update({'parentId': nullable(UUID), 'sortOrder': {'type': 'integer'}})
        schemas[kind+'Create'] = {**object_schema(fields), 'required': ['name', 'slug']}
        schemas[kind+'Patch'] = {**object_schema(fields), 'required': [], 'minProperties': 1}
    pages = [parameter('q', 'query', {**text(200), 'default': '', 'description': 'At most twenty lexical tokens.'}), parameter('page', 'query', {'type': 'integer', 'minimum': 0, 'default': 0}), parameter('size', 'query', {'type': 'integer', 'minimum': 1, 'maximum': 100, 'default': 20})]
    market = parameter('market', 'query', {'type': 'string', 'pattern': '^[A-Z][A-Z0-9_-]{1,31}$'}, True)
    sorts = ['relevance,desc', 'name,asc', 'name,desc', 'createdAt,asc', 'createdAt,desc']
    def op(api, response, params, public=False):
        result=operation(api, 'Exact closed catalogue contract. Reject unknown/repeated query parameters and request bodies on queries. Staff commands check current owned role with no organization context before replay and again under Party lock. No wholesale price or shipping label is exposed. See docs/runbooks/catalogue.md for aggregate locking, publication, availability and search behavior.', response, params)
        result['responses']['422']['description']='Catalogue rule violation or unapproved metadata/asset delivery configuration'
        if public:
            result['security']=[]
            result['responses']['304']={'description':'Current visible representation matches ETag; no body'}
            result['responses']['429']={'description':'Bounded replica public read budget exceeded'}
            result['responses']['503']={'description':'Bounded search unavailable/timeout'}
        return result
    spec['paths']['/api/v1/catalogue/products']={'get':op('API-013','ProductSummaryPage',[market,*pages,parameter('categoryId','query',UUID),parameter('brandId','query',UUID),parameter('productType','query',text(100)),parameter('sort','query',{'enum':sorts,'default':'relevance,desc'})],True)}
    spec['paths']['/api/v1/catalogue/products/{id}']={'get':op('API-014','ProductDTO',[parameter('id','path',UUID,True),market],True)}
    rec=op('API-073','PublicRecommendationDTO',[parameter('id','path',UUID,True),market,parameter('limit','query',{'type':'integer','minimum':1,'maximum':12,'default':6})],True)
    rec['description']='Public curated recommendations followed by verified aggregate popularity from paid Commerce order lines. Every result is currently published and eligible in the requested enabled market. Deterministic ordering; no behavioral profile.'
    rec['responses'].pop('304',None)
    rec['responses']['200']['content']['application/json']['schema']={'type':'array','maxItems':12,'items':REF('PublicRecommendationDTO')}
    spec['paths']['/api/v1/catalogue/products/{id}/recommendations']={'get':rec}
    cur=op('API-074','RecommendationResultDTO',[parameter('id','path',UUID,True),parameter('If-Match','header',{'type':'string','pattern':'^"(0|[1-9][0-9]{0,18})"$'},True),parameter('Idempotency-Key','header',{'type':'string','minLength':1,'maxLength':100,'pattern':'^[ -~]+$'},True),parameter('X-CSRF-TOKEN','header',{'type':'string','minLength':43,'maxLength':43},True)])
    cur['description']='Requires the current SALES role and an unscoped staff actor. Replace the recommendation set atomically under the parent product lock. Request uses exact Commerce recommendation DTOs; stale product versions return 412, matching idempotency keys replay the stored result, and no direct event is emitted.'
    cur['requestBody']={'required':True,'content':{'application/json':{'schema':object_schema({'items':{'type':'array','maxItems':100,'items':REF('RecommendationDTO')}})}}}
    cur['responses']['200']['headers']={'ETag':{'description':'Quoted parent product version','schema':{'type':'string'}}}
    spec['paths']['/api/v1/staff/products/{id}/recommendations']={'put':cur}
    knowledge=operation('API-123','Exact nigson-ai workload client with knowledge.read scope over private ingress. Only current published product data and currently enabled eligible markets are returned. publicUrl is a published public image URL. No wholesale prices, internal fields or customer data. No request body or query parameters.', 'CatalogueKnowledgeDTO', [parameter('id','path',UUID,True)])
    knowledge['security']=[{'commerceAIKnowledgeWorkload':['knowledge.read']}]
    spec['paths']['/internal/v1/catalogue/knowledge/{id}']={'get':knowledge}
    spec['components']['securitySchemes']['commerceAIKnowledgeWorkload']={'type':'oauth2','flows':{'clientCredentials':{'tokenUrl':'/realms/nigson/protocol/openid-connect/token','scopes':{'knowledge.read':'Read current published Commerce product knowledge'}}},'description':'Exact nigson-ai client, Commerce audience, knowledge.read scope and private ingress.'}
    spec['paths']['/api/v1/catalogue/categories']={'get':op('API-015','PublicCategoryDTO',[market],True)}
    spec['paths']['/api/v1/catalogue/brands']={'get':op('API-016','PublicBrandDTO',[],True)}
    for path, dto in [('/api/v1/catalogue/categories','PublicCategoryDTO'),('/api/v1/catalogue/brands','PublicBrandDTO')]:
        spec['paths'][path]['get']['responses']['200']['content']['application/json']['schema']={'type':'array','maxItems':1000,'items':REF(dto)}
    spec['paths']['/api/v1/staff/products']={'get':op('API-149','ProductAdminPage',[*pages,parameter('status','query',{'enum':['DRAFT','ACTIVE','ARCHIVED']}),parameter('sort','query',{'enum':sorts,'default':'createdAt,desc'})])}
    mutations=[(17,'post','/api/v1/staff/products','ProductCreate','ProductAdminDTO',201,False),(18,'patch','/api/v1/staff/products/{id}','ProductPatch','ProductAdminDTO',200,True),(19,'post','/api/v1/staff/products/{id}/publication','PublicationCommand','ProductAdminDTO',201,True),(20,'post','/api/v1/staff/products/{id}/skus','SkuCreate','SkuAdminDTO',201,True),(21,'patch','/api/v1/staff/skus/{id}','SkuPatch','SkuAdminDTO',200,True),(22,'post','/api/v1/staff/brands','BrandCreate','TaxonomyDTO',201,False),(23,'patch','/api/v1/staff/brands/{id}','BrandPatch','TaxonomyDTO',200,True),(24,'post','/api/v1/staff/categories','CategoryCreate','TaxonomyDTO',201,False),(25,'patch','/api/v1/staff/categories/{id}','CategoryPatch','TaxonomyDTO',200,True)]
    for number, method, path, request, response, status, versioned in mutations:
        params=[parameter('Idempotency-Key','header',{'type':'string','minLength':1,'maxLength':100,'pattern':'^[ -~]+$'},True),parameter('X-CSRF-TOKEN','header',{'type':'string','minLength':43,'maxLength':43},True)]
        if '{id}' in path: params.insert(0,parameter('id','path',UUID,True))
        if versioned: params.append(parameter('If-Match','header',{'type':'string','pattern':'^"(0|[1-9][0-9]{0,18})"$'},True))
        result=op(f'API-{number:03}',response,params)
        result['description'] += ' Requires '+('CONTENT_PUBLISHER' if number==19 else 'CONTENT_EDITOR')+'. API-020 If-Match is the parent Product version; API-021 If-Match is the SKU version.'
        result['requestBody']={'required':True,'content':{'application/json':{'schema':REF(request)}}}
        success=result['responses'].pop('200');success['headers']={'ETag':{'description':'Quoted mutation resource version','schema':{'type':'string'}}};result['responses'][str(status)]=success
        spec['paths'].setdefault(path,{})[method]=result
    return spec
