#!/usr/bin/env python3
"""Normalize only synthetic local catalogue fixtures for the reviewed product API."""
import json,re,os
from pathlib import Path
from seed_demo import docker,literal,uid

def main():
 if os.environ.get('DOCKER_HOST') and not os.environ['DOCKER_HOST'].startswith('unix://'):raise ValueError('Local Docker required')
 if os.environ.get('APP_ENV','local')!='local':raise ValueError('Local environment required')
 if not json.loads(docker('context','inspect'))[0]['Endpoints']['docker']['Host'].startswith('unix://'):raise ValueError('Local Docker required')
 config=json.loads(docker('compose','config','--format','json'))
 if config.get('name')!='nigson-local' or config['services']['postgres']['environment'].get('APP_ENV')!='local':raise ValueError('Local database required')
 def sql(command):return docker('compose','exec','-T','postgres','psql','-X','-qAt','-U','nigson_bootstrap','-d','nigson','-v','ON_ERROR_STOP=1',input=command)
 rows=json.loads(sql("SELECT jsonb_agg(jsonb_build_object('id',id,'creator',created_by,'specifications',specifications)) FROM commerce.product WHERE created_by IN ('demo-electronics-v1','demo-seed-v1');"))
 if any(r['creator'] not in ('demo-electronics-v1','demo-seed-v1') for r in rows):raise ValueError('Synthetic-only catalogue required')
 statements=['BEGIN;',"SET LOCAL lock_timeout='5s';"];keys=set()
 for row in rows:
  specs={re.sub(r'[A-Z]',lambda m:'_'+m[0].lower(),k):v for k,v in row['specifications'].items()};keys.update(specs)
  value=literal(json.dumps(specs))+'::jsonb'
  statements.append(f"UPDATE commerce.product SET specifications={value},division_code='SYNTHETIC_ELECTRONICS',version=version+1,revision=revision+1,updated_by='local-demo-metadata-repair',updated_at=now() WHERE id={literal(row['id'])} AND (specifications IS DISTINCT FROM {value} OR division_code IS DISTINCT FROM 'SYNTHETIC_ELECTRONICS');")
 statements.append('COMMIT;');sql('\n'.join(statements))
 units=json.loads(sql("SELECT jsonb_agg(t) FROM (SELECT unit AS code,jsonb_agg(distinct quantity_scale) AS scales FROM commerce.sku GROUP BY unit)t;"))
 metadata={'schemaVersion':1,'units':units,'divisions':[{'code':'SYNTHETIC_ELECTRONICS','attributes':[{'key':k,'type':'TEXT','unit':None,'maximumLength':1000} for k in sorted(keys)]}]}
 target=Path(__file__).resolve().parents[2]/'.build/admin-synthetic-catalogue-policy.json';target.write_text(json.dumps({'value':metadata,'active':True,'reason':'Enable synthetic local catalogue validation'}))
 value=literal(json.dumps(metadata))+'::jsonb'
 sql("BEGIN; SELECT pg_advisory_xact_lock(hashtextextended('configuration:catalogue.metadata',0)); INSERT INTO commerce.feature_configuration(id,created_by,updated_by,key,revision,version,value,active,approved_by) SELECT "+literal(uid('local-demo-catalogue-metadata'))+",'local-demo-metadata','local-demo-metadata','catalogue.metadata',1,1,"+value+",true,'local-demo-metadata' WHERE NOT EXISTS(SELECT 1 FROM commerce.feature_configuration WHERE key='catalogue.metadata'); COMMIT;")
 sql("BEGIN; UPDATE experience.content_revision SET created_by='00000000-0000-4000-8000-000000000001',updated_by='local-demo-metadata-repair' WHERE created_by='demo-seed-v1' AND state='DRAFT'; COMMIT;")
 print('PASS: synthetic catalogue keys/policy and draft CMS actor fields repaired locally.')
if __name__=='__main__':main()
