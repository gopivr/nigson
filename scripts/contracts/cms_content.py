"""Experience-owned CMS and publication APIs 076-086."""
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]

def extend(spec):
    from authorization import UUID, VERSION, object_schema, parameter, REF
    s=spec['components']['schemas'];paths=spec['paths'];scopes=spec['components']['securitySchemes']['workload']['flows']['clientCredentials']['scopes']
    block_schemas=[]
    for kind,fields in [('TEXT',{'text':{'type':'string','maxLength':20000}}),('IMAGE',{'assetId':UUID,'alt':{'type':'string','maxLength':300}}),('VIDEO',{'provider':{'enum':['YOUTUBE','VIMEO']},'videoId':{'type':'string','maxLength':100},'caption':{'type':'string','maxLength':500}}),('PERSON',{'name':{'type':'string','maxLength':160},'title':{'type':'string','maxLength':160},'bio':{'type':'string','maxLength':10000},'assetId':{'anyOf':[UUID,{'type':'null'}]}}),('LINK',{'label':{'type':'string','maxLength':160},'url':{'type':'string','format':'uri','pattern':'^https:'}}),('MAP',{'channelKey':{'type':'string','maxLength':80}}),('PRODUCT_LIST',{'productIds':{'type':'array','maxItems':30,'items':UUID}}),('FAQ',{'question':{'type':'string','maxLength':500},'answer':{'type':'string','maxLength':5000}})]:
        block_schemas.append({'type':'object','additionalProperties':False,'required':['type',*fields.keys()],'properties':{'type':{'const':kind},**fields}})
    content_body=object_schema({'schemaVersion':{'const':1},'blocks':{'type':'array','maxItems':200,'items':{'oneOf':block_schemas}}})
    seo=object_schema({'title':{'type':'string','maxLength':70},'description':{'type':'string','maxLength':180},'canonicalUrl':{'anyOf':[{'type':'string','format':'uri','pattern':'^https:'},{'type':'null'}]},'robots':{'enum':['INDEX','NOINDEX']},'alternates':{'type':'array','maxItems':30,'items':object_schema({'locale':{'type':'string','maxLength':16},'url':{'type':'string','format':'uri','pattern':'^https:'}})},'structuredData':{'type':'object','maxProperties':50}})
    s['ContentBody']=content_body;s['SeoDTO']=seo
    s['ContentDTO']=object_schema({'id':UUID,'type':{'enum':['HOME','ABOUT','LEADERSHIP','DIVISION','BLOG','VIDEO','FAQ','CAREERS','CONTACT']},'slug':{'type':'string','maxLength':220},'locale':{'type':'string','maxLength':16},'revision':{'type':'integer','minimum':1},'title':{'type':'string','maxLength':220},'body':REF('ContentBody'),'metadata':REF('SeoDTO'),'publishedAt':{'type':'string','format':'date-time'}})
    s['ContentRevisionInput']=object_schema({'type':{'enum':['HOME','ABOUT','LEADERSHIP','DIVISION','BLOG','VIDEO','FAQ','CAREERS','CONTACT']},'slug':{'type':'string','maxLength':220},'locale':{'type':'string','maxLength':16},'title':{'type':'string','maxLength':220},'body':REF('ContentBody'),'metadata':REF('SeoDTO')})
    s['ContentRevisionEdit']=object_schema({'title':{'type':'string','maxLength':220},'body':REF('ContentBody'),'metadata':REF('SeoDTO')})
    s['ContentRevisionDTO']=object_schema({'id':UUID,'contentItemId':UUID,'revision':{'type':'integer','minimum':1},'state':{'enum':['DRAFT','REVIEW','APPROVED','PUBLISHED','WITHDRAWN','ARCHIVED']},'type':{'type':'string'},'slug':{'type':'string'},'locale':{'type':'string'},'title':{'type':'string'},'body':REF('ContentBody'),'metadata':REF('SeoDTO'),'version':VERSION,'scheduledAt':{'anyOf':[{'type':'string','format':'date-time'},{'type':'null'}]},'publishedAt':{'anyOf':[{'type':'string','format':'date-time'},{'type':'null'}]}})
    s['ContentReview']=object_schema({'reason':{'type':'string','minLength':1,'maxLength':500}})
    s['ContentApproval']=object_schema({'approve':{'type':'boolean'},'reason':{'type':'string','minLength':1,'maxLength':500}})
    s['ContentPublication']=object_schema({'action':{'enum':['PUBLISH','WITHDRAW']},'scheduleAt':{'type':'string','format':'date-time'},'reason':{'type':'string','minLength':1,'maxLength':500}});s['ContentPublication']['required'].remove('scheduleAt')
    s['ChannelConfigurationInput']=object_schema({'kind':{'enum':['WHATSAPP','SOCIAL','MAP','LEAD_RECIPIENT_GROUP','SEO']},'key':{'type':'string','maxLength':80},'publicValue':{'type':'object'},'active':{'type':'boolean'}})
    s['ChannelConfigurationDTO']=object_schema({'id':UUID,'kind':{'type':'string'},'key':{'type':'string'},'publicValue':{'type':'object'},'active':{'type':'boolean'},'version':VERSION})
    s['ContentPage']=object_schema({'content':{'type':'array','maxItems':100,'items':REF('ContentDTO')},'page':{'type':'integer','minimum':0},'size':{'type':'integer','minimum':1,'maximum':100},'totalElements':VERSION,'totalPages':VERSION,'sort':{'type':'string'}})
    s['SitemapEntry']=object_schema({'url':{'type':'string','format':'uri','pattern':'^https:'},'lastModified':{'type':'string','format':'date-time'},'locale':{'type':'string','maxLength':16},'alternateUrls':{'type':'array','items':object_schema({'locale':{'type':'string'},'url':{'type':'string','format':'uri','pattern':'^https:'}})}})
    s['ContentSitemap']=object_schema({'entries':{'type':'array','maxItems':10000,'items':REF('SitemapEntry')}})
    channel_value={'type':'object'}
    s['PublicChannels']=object_schema({'whatsapp':{'type':'array','items':channel_value},'social':{'type':'array','items':channel_value},'maps':{'type':'array','items':channel_value}})
    def params(path=False,match=False,key=False):
        out=[]
        if path:out.append(parameter('id','path',UUID,True))
        if match:out.append(parameter('If-Match','header',{'type':'string','pattern':'^"(0|[1-9][0-9]{0,18})"$'},True))
        if key:out.append(parameter('Idempotency-Key','header',{'type':'string','minLength':1,'maxLength':100,'pattern':'^[ -~]+$'},True))
        return out
    def op(api,desc,response,parameters=(),role=None,scope=None,method='get',body=None,status='200',public=False):
        x={'operationId':api,'description':desc,'parameters':list(parameters),'responses':{status:{'description':'Success; no-store for private responses','headers':{'Cache-Control':{'schema':{'const':'no-store'}}},'content':{'application/json':{'schema':REF(response)}}},'400':{'description':'Invalid request'},'401':{'description':'Authentication required'},'403':{'description':'Access denied'},'404':{'description':'Not found'},'409':{'description':'Conflict or invalid transition'},'412':{'description':'Stale version'},'500':{'description':'Safe internal error'}}}
        if public:x['security']=[]
        else:
            x['security']=[{'workload':[scope],'signedActor':[]}]
            scopes[scope]='Exact current signed '+str(role)+' actor delegated by Commerce BFF'
        if body:x['requestBody']={'required':True,'content':{'application/json':{'schema':REF(body)}}}
        return x
    paths['/api/v1/content/{type}/{slug}']={'get':op('API-076','Public current approved published revision only. Optional market fails closed when no owner-local eligibility is configured. Never serves preview/draft.', 'ContentDTO',[parameter('type','path',{'type':'string'},True),parameter('slug','path',{'type':'string','maxLength':220},True),parameter('locale','query',{'type':'string','maxLength':16,'default':'en'}),parameter('market','query',{'type':'string','maxLength':8})],public=True)}
    paths['/api/v1/content']={'get':op('API-077','Bounded stable page of current approved published content only. Reject unknown/repeated query parameters.', 'ContentPage',[parameter('type','query',{'type':'string'},True),parameter('locale','query',{'type':'string','default':'en'}),parameter('page','query',{'type':'integer','minimum':0,'default':0}),parameter('size','query',{'type':'integer','minimum':1,'maximum':100,'default':20}),parameter('sort','query',{'type':'string','enum':['publishedAt,asc','publishedAt,desc'],'default':'publishedAt,desc'})],public=True)}
    paths['/api/v1/staff/content']={'post':op('API-078','CONTENT_EDITOR; create content item and immutable draft revision in one owner-local transaction; idempotent replay.', 'ContentRevisionDTO',params(key=True),role='CONTENT_EDITOR',scope='content.write_WITH_SIGNED_ACTOR',method='post',body='ContentRevisionInput',status='201')}
    paths['/api/v1/staff/content/{id}/revisions']={'post':op('API-079','CONTENT_EDITOR; create a new draft revision, never overwrite published text. If-Match is the content item version.', 'ContentRevisionDTO',params(True,True,True),role='CONTENT_EDITOR',scope='content.write_WITH_SIGNED_ACTOR',method='post',body='ContentRevisionEdit',status='201')}
    paths['/api/v1/staff/content-revisions/{id}/review']={'post':op('API-080','CONTENT_EDITOR; DRAFT to REVIEW only; actor/reason audited.', 'ContentRevisionDTO',params(True,True,True),role='CONTENT_EDITOR',scope='content.review_WITH_SIGNED_ACTOR',method='post',body='ContentReview')}
    paths['/api/v1/staff/content-revisions/{id}/approval']={'post':op('API-081','CONTENT_PUBLISHER; REVIEW to APPROVED or back to DRAFT; public visibility requires a separate publication command.', 'ContentRevisionDTO',params(True,True,True),role='CONTENT_PUBLISHER',scope='content.approve_WITH_SIGNED_ACTOR',method='post',body='ContentApproval')}
    paths['/api/v1/staff/content-revisions/{id}/publication']={'post':op('API-082','CONTENT_PUBLISHER; publish an approved immutable revision or withdraw it immediately. State, pointer, audit, idempotency and EVT-017/018 outbox commit atomically. Future schedules publish from the owner scheduler.', 'ContentRevisionDTO',params(True,True,True),role='CONTENT_PUBLISHER',scope='content.publish_WITH_SIGNED_ACTOR',method='post',body='ContentPublication')}
    paths['/api/v1/staff/content-revisions/{id}/preview']={'get':op('API-083','CONTENT_EDITOR only; private no-store draft/revision preview.', 'ContentRevisionDTO',params(True),role='CONTENT_EDITOR',scope='content.read_WITH_SIGNED_ACTOR')}
    paths['/api/v1/seo/sitemap']={'get':op('API-084','Public sitemap contains only configured canonical HTTPS URLs for current approved public Experience content; empty until approved SEO origin configuration exists.', 'ContentSitemap',public=True)}
    paths['/api/v1/channels']={'get':op('API-085','Public active channel projection; never returns lead recipient groups or provider secrets.', 'PublicChannels',public=True)}
    paths['/api/v1/staff/channel-configurations/{id}']={'put':op('API-086','ADMIN with current signed assertion and recent MFA; versioned typed channel configuration update. No channel values are preconfigured.', 'ChannelConfigurationDTO',params(True,True,True),role='ADMIN',scope='channels.write_WITH_SIGNED_ADMIN',method='put',body='ChannelConfigurationInput')}
    return spec

