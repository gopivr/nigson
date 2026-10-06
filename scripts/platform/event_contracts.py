"""Generate closed wire schemas and exact routing from master section16."""
import json
from pathlib import Path
import re
ROOT=Path(__file__).resolve().parents[2]
s=(ROOT/'BACKEND_IMPLEMENTATION_MASTER.md').read_text();s=s[s.index('# 16.'):s.index('# 17.')]
registry={}
def field(kind):
 nullable=kind.endswith('|null');kind=kind.removesuffix('|null')
 if kind.startswith('['): value={'type':'array','maxItems':100,'items':field(kind[1:-1])}
 elif kind=='UUID': value={'type':'string','format':'uuid','pattern':'^(?!00000000-0000-0000-0000-000000000000$)[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$'}
 elif kind in ('int','int64'):value={'type':'integer','minimum':0,'maximum':9223372036854775807}
 elif kind=='boolean':value={'type':'boolean'}
 elif kind=='Instant':value={'type':'string','format':'date-time','maxLength':40,'pattern':r'^\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(\.\d{1,9})?(Z|\+00:00)$'}
 elif kind=='ISO4217':value={'type':'string','pattern':'^[A-Z]{3}$'}
 elif kind in ('Money','Quantity'):value={'type':'string','pattern':r'^-?\d{1,'+('15' if kind=='Money' else '17')+r'}(\.\d{1,'+('4' if kind=='Money' else '3')+r'})?$'}
 elif kind=='string':value={'type':'string','maxLength':1000,'minLength':1}
 else:value={'type':'string','enum':kind.split('|')}
 if nullable:value={'anyOf':[value,{'type':'null'}]}
 return value
for id,name,body in re.findall(r'### (EVT-\d+) ([^\n]+)\n(.*?)(?=### |\Z)',s,re.S):
 producer=re.search(r'Producer / phase:\*\* (.*?) / BP(\d)',body)
 consumers=re.search(r'Consumers:\*\* (.*)',body)[1]
 props={n:field(t) for n,t in (f.split(':',1) for f in re.search(r'Payload:\*\* `([^`]+)`',body)[1].split(','))}
 payload={'type':'object','properties':props,'required':list(props),'additionalProperties':False}
 envelope={'event_id':field('UUID'),'event_type':{'const':name},'schema_version':{'const':1},'producer':{'enum':producer[1].split(' or ')},'occurred_at':field('Instant'),'aggregate_id':field('UUID'),'aggregate_version':field('int64'),'organization_id':field('UUID|null'),'correlation_id':field('UUID'),'traceparent':{'anyOf':[{'type':'string','pattern':'^00-(?!0{32}-)[a-f0-9]{32}-(?!0{16}-)[a-f0-9]{16}-[a-f0-9]{2}$'},{'type':'null'}]},'payload':payload}
 schema={'$schema':'https://json-schema.org/draft/2020-12/schema','$id':'urn:nigson:event:'+name,'type':'object','properties':envelope,'required':list(envelope),'additionalProperties':False}
 (ROOT/'contracts/events'/f'{id}-{name}.json').write_text(json.dumps(schema,indent=2)+'\n')
 registry[name]={'id':id,'phase':int(producer[2]),'producers':producer[1].split(' or '),'consumers':[o for o in ('integration','experience','commerce','ai') if re.search(r'\b'+re.escape(o)+r'\b',consumers)],'schema':schema}
(ROOT/'shared/common-events/src/main/resources/events/registry.json').write_text(json.dumps(registry,indent=2)+'\n')
(ROOT/'contracts/events/registry.json').write_text(json.dumps(registry,indent=2)+'\n')

(ROOT/'services/ai/src/nigson_ai/platform/registry.json').write_text(json.dumps(registry,indent=2)+'\n')
