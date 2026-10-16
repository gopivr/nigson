"""Real previous BP1 upgrade and empty additive rollback/reapply for both carrier owners."""
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
    evidence=ROOT/'.build/carrier-cancellation-upgrade-evidence'
    evidence.mkdir(parents=True,exist_ok=True)
    verify.EVIDENCE=evidence
    before=verify.preserved()
    network='nigson-test-carrier-upgrade-'+uuid.uuid4().hex[:10]
    verify.docker('network','create',network)
    db=None
    try:
        db=verify.Database(network,'postgres')
        libraries=':'.join(str(p) for p in (ROOT/'.build/m2').rglob('*.jar'))
        classes=evidence/'classes';classes.mkdir(exist_ok=True)
        subprocess.run([str(Path(os.environ['JAVA_HOME'])/'bin/javac'),'-proc:none','-cp',libraries,'-d',str(classes),str(ROOT/'scripts/carrier/CancellationIncrementProbe.java')],check=True)
        for owner in ('commerce','integration'):
            wrapper=ROOT/f'services/{owner}/src/main/resources/db/changelog/releases/BP1.yaml'
            baseline=json.loads(wrapper.read_text())
            baseline['databaseChangeLog']=[x for x in baseline['databaseChangeLog'] if 'carrier-cancellation' not in x['include']['file']]
            temporary=evidence/f'{owner}-previous-BP1.yaml';temporary.write_text(json.dumps(baseline,indent=2)+'\n')
            output,error=verify.docker('run','--rm','--network',network,'--read-only','--tmpfs','/tmp','-v',str(ROOT)+':/app/source:ro','-v',str(temporary)+f':/app/source/services/{owner}/src/main/resources/db/changelog/releases/BP1.yaml:ro','-e','APP_ENV=test','-e','MIGRATION_OWNER='+owner,'-e','MIGRATION_RELEASE=BP1','-e','MIGRATION_JDBC_URL=jdbc:postgresql://postgres:5432/nigson','-e',f'MIGRATION_PASSWORD=mock-{owner}-migration','nigson/migration:local')
            (evidence/f'{owner}-previous-bp1.log').write_text(output+'\n'+error)
            assert db.sql(f"SELECT to_regclass('{owner}.carrier_cancellation') IS NULL")=='t'
            original=db.sql(f'SELECT id||md5sum FROM {owner}.databasechangelog ORDER BY id')
            marker=str(uuid.uuid4())
            if owner=='commerce':
                db.sql(f"INSERT INTO commerce.product(id,created_by,updated_by,name,slug,description) VALUES ('{marker}','synthetic-upgrade-marker','test','Synthetic upgrade marker','synthetic-{marker}','Synthetic')",'nigson_commerce_app')
                marker_sql=f"SELECT count(*) FROM commerce.product WHERE id='{marker}'"
            else:
                db.sql(f"INSERT INTO integration.carrier_booking(id,created_by,updated_by,shipment_id,business_key,provider,mode,state,request_hash,tracking_number) VALUES ('{marker}','synthetic-upgrade-marker','test','{uuid.uuid4()}','synthetic-{marker}','FEDEX','MOCK','BOOKED','{'0'*64}','123456789012')",'nigson_integration_app')
                marker_sql=f"SELECT count(*) FROM integration.carrier_booking WHERE id='{marker}' AND state='BOOKED' AND tracking_number='123456789012'"
            db.migrate(owner,1)
            assert db.sql(f"SELECT id||md5sum FROM {owner}.databasechangelog WHERE id<>'{owner}-20261016-carrier-001' ORDER BY id")==original
            assert db.sql(marker_sql)=='1'
            fingerprint=db.fingerprint();db.migrate(owner,1);assert fingerprint==db.fingerprint()
            output,error=verify.docker('run','--rm','--network',network,'--read-only','--tmpfs','/tmp','-v',str(ROOT)+':/app/source:ro','-v',str(classes)+':/app/check:ro','-e',f'MIGRATION_PASSWORD=mock-{owner}-migration','--entrypoint','java','nigson/migration:local','-cp','/app/check:/app/lib/*','CancellationIncrementProbe',owner)
            (evidence/f'{owner}-rollback-reapply.log').write_text(output+'\n'+error)
            assert fingerprint==db.fingerprint();assert db.sql(marker_sql)=='1'
        result={'result':'PASS','owners':['commerce','integration'],'checks':['Previous accepted BP1 without cancellation applied','Prior changeset checksums and representative product/booked carrier evidence preserved','Repeated additive upgrade unchanged','Actual Liquibase empty cancellation rollback/reapply preserves prior data'],'local_state_preserved':True}
        (evidence/'acceptance.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result))
    finally:
        if db is not None:verify.docker('rm','-f','-v',db.name)
        verify.docker('network','rm',network)
        assert before==verify.preserved()
if __name__=='__main__':main()
