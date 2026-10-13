"""Reviewed implemented private integration contracts; no reserved APIs emitted."""
import argparse
import json
from pathlib import Path
ROOT=Path(__file__).resolve().parents[2]
REF=lambda name:{'$ref':'#/components/schemas/'+name}
def obj(properties):
 return {'type':'object','additionalProperties':False,'required':list(properties),'properties':properties}
def param(name,location,schema,required=False):
 return {'name':name,'in':location,'required':required,'schema':schema}
def contract():
 uuid={'type':'string','format':'uuid'}
 state={'type':'string','enum':['PENDING','CLAIMED','SUCCEEDED','FAILED','UNKNOWN']}
 kind={'type':'string','enum':['SHEETS_SYNC','LOGISTICS_CREATE','PAYMENT_RECONCILE','ASSET_SCAN','REPORT_EXPORT']}
 schemas={'JobDTO':obj({'id':uuid,'state':state,'attemptCount':{'type':'integer','minimum':0},'nextAttemptAt':{'type':'string','format':'date-time'},'lastErrorCode':{'type':['string','null'],'maxLength':60,'pattern':'^[A-Z][A-Z0-9_]{0,59}$'}}),'JobRetry':obj({'reason':{'type':'string','minLength':1,'maxLength':500}}),'JobPage':obj({'items':{'type':'array','maxItems':100,'items':REF('JobDTO')},'page':{'type':'integer','minimum':0},'size':{'type':'integer','minimum':1,'maximum':100},'totalElements':{'type':'integer','format':'int64','minimum':0},'totalPages':{'type':'integer','format':'int64','minimum':0}})}
 def operation(api,status,response):
  return {'operationId':api,'security':[{'workload':[]}],'responses':{str(status):{'description':'Success','content':{'application/json':{'schema':REF(response)}}},**{str(c):{'description':d,'content':{'application/problem+json':{'schema':{'$ref':'../problem/problem.schema.json'}}}} for c,d in [(400,'Invalid input'),(401,'Authentication required'),(403,'Access denied'),(404,'Not found'),(409,'Conflict'),(412,'Version mismatch'),(415,'Unsupported content type'),(422,'Provider rule violation'),(500,'Safe internal error'),(503,'Provider unavailable or outcome unknown')]}}}
 read=operation('API-140',200,'JobPage');read['description']='Private SERVICE_COMMERCE jobs.read; no actor assertion, cookie or origin. Closed safe paginated views; no request body; unknown/repeated parameters denied.'
 read['parameters']=[param('kind','query',kind),param('state','query',state),param('page','query',{'type':'integer','minimum':0,'default':0}),param('size','query',{'type':'integer','minimum':1,'maximum':100,'default':20}),param('sort','query',{'type':'string','enum':['nextAttemptAt,asc','nextAttemptAt,desc','id,asc','id,desc'],'default':'nextAttemptAt,asc'})]
 retry=operation('API-141',202,'JobDTO');retry['security']=[{'workload':[],'signedActor':[]}];retry['description']='Private SERVICE_COMMERCE jobs.retry_WITH_SIGNED_STAFF; current source OPERATIONS and recent MFA. FAILED only; UNKNOWN must reconcile and CLAIMED cannot be bypassed. Same command key replays actual acknowledgement without another queue transition; original business key and attempt count retained. No provider HTTP in command transaction.'
 retry['parameters']=[param('id','path',uuid,True),param('If-Match','header',{'type':'string','pattern':'^"(0|[1-9][0-9]{0,18})"$'},True),param('Idempotency-Key','header',{'type':'string','minLength':1,'maxLength':100},True),param('X-Correlation-ID','header',uuid,True)]
 retry['requestBody']={'required':True,'content':{'application/json':{'schema':REF('JobRetry')}}};retry['responses']['202']['headers']={'ETag':{'description':'Actual accepted job version','schema':{'type':'string'}}}
 text=lambda maximum:{'type':'string','minLength':1,'maxLength':maximum}
 nullable=lambda maximum:{'type':['string','null'],'maxLength':maximum}
 quantity={'type':'string','pattern':r'^(?=.*[1-9])[0-9]{1,17}(\.[0-9]{1,3})?$','maxLength':21}
 dimension={'type':'string','pattern':r'^(?=.*[1-9])[0-9]{1,15}(\.[0-9]{1,4})?$','maxLength':20}
 address=obj({'recipient':text(160),'phone':{'type':'string','pattern':r'^\+[1-9][0-9]{7,14}$'},'line1':text(200),'line2':nullable(200),'city':text(100),'region':text(100),'postalCode':nullable(24),'countryCode':{'const':'NG','type':'string'}})
 service={'type':'string','pattern':'^[A-Z][A-Z0-9_]{0,79}$','maxLength':80}
 schemas.update({'CarrierAddress':address,'CarrierPackage':obj({'skuId':uuid,'quantity':quantity,'weightKg':quantity,'lengthCm':dimension,'widthCm':dimension,'heightCm':dimension}),'CarrierQuoteInput':obj({'commandId':uuid,'cartId':uuid,'market':{'type':'string','const':'NG'},'currency':{'type':'string','const':'NGN'},'shipper':REF('CarrierAddress'),'destination':REF('CarrierAddress'),'packages':{'type':'array','minItems':1,'maxItems':100,'items':REF('CarrierPackage')},'serviceCode':service}),'CarrierQuoteDTO':obj({'provider':{'type':'string','const':'FEDEX'},'serviceCode':service,'currency':{'type':'string','const':'NGN'},'amount':{'type':'string','pattern':r'^[0-9]{1,15}\.[0-9]{2}$'},'expiresAt':{'type':'string','format':'date-time'},'providerReference':nullable(160)})})
 schemas['CarrierQuoteInput']['required'].remove('serviceCode')
 carrier=operation('API-182',200,'CarrierQuoteDTO');carrier['security']=[{'workload':[],'signedActor':[]}];carrier['description']='Private SERVICE_COMMERCE carrier.quote_WITH_SIGNED_ACTOR. Closed NG/NGN server address/packing only. Configured account/service/pickup and current approval; exact single ACCOUNT rate in NGN. No exchange conversion, route assumption or manual fallback. Actual OAuth/rate HTTP occurs outside local locks; commandId durably replays the original acknowledgement/audit. Returned rate expires within five minutes; commerce must revalidate the cart/address/packages before binding. This command never books or dispatches.'
 carrier['responses']['200']['headers']={'Cache-Control':{'schema':{'const':'no-store'}},'X-Nigson-Carrier-Mode':{'description':'Actual adapter configuration; TEST transport maps to SANDBOX. Source must match its approved mode before binding a fee.','schema':{'type':'string','enum':['MOCK','SANDBOX','LIVE']}}}
 carrier['parameters']=[param('X-Correlation-ID','header',uuid,True)];carrier['requestBody']={'required':True,'content':{'application/json':{'schema':REF('CarrierQuoteInput')}}}
 return {'openapi':'3.1.0','info':{'title':'Nigson private integration API','version':'1.1.0'},'paths':{'/internal/v1/jobs':{'get':read},'/internal/v1/jobs/{id}/retry':{'post':retry},'/internal/v1/carrier/quotes':{'post':carrier}},'components':{'schemas':schemas,'securitySchemes':{'workload':{'type':'http','scheme':'bearer','bearerFormat':'JWT','description':'Exact registered Keycloak client/audience/scope; private ingress only'},'signedActor':{'type':'apiKey','in':'header','name':'X-Nigson-Actor','description':'ES256 assertion binds source identity, operation, method/path/body/correlation; endpoint-specific roles/MFA apply'}}}}
if __name__=='__main__':
 parser=argparse.ArgumentParser();parser.add_argument('--check',action='store_true');args=parser.parse_args();path=ROOT/'contracts/openapi/integration.yaml';value=json.dumps(contract(),indent=2)+'\n'
 if args.check:
  if not path.exists() or path.read_text()!=value:raise SystemExit('FAIL: integration OpenAPI differs from reviewed generator')
  print('PASS: reviewed implemented integration OpenAPI matches generator')
 else:path.write_text(value)
