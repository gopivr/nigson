"""Actual previous-schema notification upgrade, checksum/data retention and empty/populated rollback."""
from pathlib import Path
import json
import os
import subprocess
import sys
import uuid
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/database'))
import verify

def main():
    evidence=ROOT/'.build/messaging-intake-upgrade-evidence';evidence.mkdir(parents=True,exist_ok=True);verify.EVIDENCE=evidence
    before=verify.preserved();network='nigson-test-message-upgrade-'+uuid.uuid4().hex[:10];verify.docker('network','create',network);db=None
    try:
        db=verify.Database(network,'postgres')
        libraries=':'.join(str(p) for p in (ROOT/'.build/m2').rglob('*.jar'));classes=evidence/'classes';classes.mkdir(exist_ok=True)
        subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/javac'),'-proc:none','-cp',libraries,'-d',str(classes),str(ROOT/'scripts/messaging/NotificationIncrementProbe.java')],check=True)
        wrapper=ROOT/'services/integration/src/main/resources/db/changelog/releases/BP1.yaml';baseline=json.loads(wrapper.read_text());baseline['databaseChangeLog']=[row for row in baseline['databaseChangeLog'] if not any(name in row['include']['file'] for name in ('notification-dispatch','staff-reply-envelope'))]
        temporary=evidence/'integration-previous-BP1.yaml';temporary.write_text(json.dumps(baseline,indent=2)+'\n')
        output,error=verify.docker('run','--rm','--network',network,'--read-only','--tmpfs','/tmp','-v',str(ROOT)+':/app/source:ro','-v',str(temporary)+':/app/source/services/integration/src/main/resources/db/changelog/releases/BP1.yaml:ro','-e','APP_ENV=test','-e','MIGRATION_OWNER=integration','-e','MIGRATION_RELEASE=BP1','-e','MIGRATION_JDBC_URL=jdbc:postgresql://postgres:5432/nigson','-e','MIGRATION_PASSWORD=mock-integration-migration','nigson/migration:local')
        (evidence/'integration-previous-bp1.log').write_text(output+'\n'+error);assert db.sql("SELECT to_regclass('integration.notification_dispatch') IS NULL")=='t'
        original=db.sql('SELECT id||md5sum FROM integration.databasechangelog ORDER BY id');marker=str(uuid.uuid4());resource=str(uuid.uuid4())
        db.sql(f"INSERT INTO integration.notification_job(id,created_by,updated_by,business_key,owner_service,resource_id,template_code,template_revision,classification,state,next_attempt_at,channel,provider_mode) VALUES ('{marker}','synthetic-upgrade','test','synthetic-legacy-{marker}','experience','{resource}','CONTACT_VERIFICATION',1,'TRANSACTIONAL','UNKNOWN',now(),'SMS','MOCK')",'nigson_integration_app')
        marker_sql=f"SELECT count(*) FROM integration.notification_job WHERE id='{marker}' AND state='UNKNOWN'"
        db.migrate('integration',1);assert db.sql("SELECT id||md5sum FROM integration.databasechangelog WHERE id NOT IN ('integration-20261017-msg-001','integration-20261017-msg-001-envelope') ORDER BY id")==original;assert db.sql(marker_sql)=='1'
        fingerprint=db.fingerprint();db.migrate('integration',1);assert fingerprint==db.fingerprint()
        def probe(mode):
            output,error=verify.docker('run','--rm','--network',network,'--read-only','--tmpfs','/tmp','-v',str(ROOT)+':/app/source:ro','-v',str(classes)+':/app/check:ro','-e','MIGRATION_PASSWORD=mock-integration-migration','--entrypoint','java','nigson/migration:local','-cp','/app/check:/app/lib/*','NotificationIncrementProbe',mode)
            (evidence/f'integration-{mode}-rollback.log').write_text(output+'\n'+error)
        probe('empty');assert fingerprint==db.fingerprint();assert db.sql(marker_sql)=='1'
        template=str(uuid.uuid4());generation=str(uuid.uuid4());correlation=str(uuid.uuid4());account='AC'+'0'*32;service='MG'+'1'*32
        db.sql(f"INSERT INTO integration.notification_template(id,created_by,updated_by,code,revision,locale,channel,body,classification,active) VALUES ('{template}','synthetic-upgrade','test','CONTACT_VERIFICATION',1,'en-NG','SMS','Synthetic {{{{code}}}}','TRANSACTIONAL',true)",'nigson_integration_app')
        snapshot=json.dumps({'id':template,'code':'CONTACT_VERIFICATION','revision':1,'locale':'en-NG','channel':'SMS','classification':'TRANSACTIONAL','body':'Synthetic {{code}}','providerTemplateId':None,'variablesSchema':{}})
        nonce='0'*12;ciphertext='1'*64
        db.sql(f"INSERT INTO integration.notification_dispatch(id,created_by,updated_by,template_id,locale,template_snapshot,template_hash,account_sid,messaging_service_sid,account_hash,generation,source_occurred_at,correlation_id,stage,command_hash,recipient_ciphertext,variables_ciphertext,context_key_revision,context_basis) VALUES ('{marker}','synthetic-upgrade','test','{template}','en-NG','{snapshot}'::jsonb,'{'0'*64}','{account}','{service}','{'1'*64}','{generation}',now(),'{correlation}','UNKNOWN','{'2'*64}','{nonce}{ciphertext}','{nonce}{ciphertext}','test-r1','VERIFIED_DESTINATION')",'nigson_integration_app')
        history=db.sql('SELECT id||md5sum FROM integration.databasechangelog ORDER BY id');probe('populated');assert history==db.sql('SELECT id||md5sum FROM integration.databasechangelog ORDER BY id');assert db.sql(marker_sql)=='1';assert db.fingerprint()==fingerprint
        result={'result':'PASS','owner':'integration','checks':['Previous accepted BP1 applied without dispatch table','Existing checksums and legacy UNKNOWN notification retained','Repeated upgrade unchanged','Actual empty staff-reply envelope rollback/reapply','Actual populated staff-reply context rollback refusal preserves encrypted intent and changelog'],'local_state_preserved':True}
        (evidence/'acceptance.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
    finally:
        if db is not None:verify.docker('rm','-f','-v',db.name)
        verify.docker('network','rm',network);assert before==verify.preserved()
if __name__=='__main__':main()
