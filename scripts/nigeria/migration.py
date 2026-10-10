"""Fresh/previous-BP1/rollback/conflict qualification for approved disabled metadata."""
from pathlib import Path
import os,sys,json,subprocess,uuid,hashlib
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/database'))
import verify
from verify import Database,docker,preserved
from nigeria import IDS
EVIDENCE=ROOT/'.build/ngn001/migration-evidence'
EVIDENCE.mkdir(parents=True,exist_ok=True);verify.EVIDENCE=EVIDENCE
WRAPPER='services/commerce/src/main/resources/db/changelog/releases/BP1.yaml'
def previous(db):
    old=json.loads((ROOT/WRAPPER).read_text());old['databaseChangeLog']=[item for item in old['databaseChangeLog'] if '024-bp1-nigeria-metadata' not in item.get('include',{}).get('file','')]
    path=ROOT/'.build/ngn001/previous-BP1.yaml';path.write_text(json.dumps(old,indent=2)+'\n')
    out,error=docker('run','--rm','--network',db.network,'--read-only','--tmpfs','/tmp','-v',str(ROOT)+':/app/source:ro','-v',str(path)+':/app/source/'+WRAPPER+':ro','-e','APP_ENV=test','-e','MIGRATION_OWNER=commerce','-e','MIGRATION_RELEASE=BP1','-e','MIGRATION_JDBC_URL=jdbc:postgresql://postgres:5432/nigson','-e','MIGRATION_PASSWORD=mock-commerce-migration','nigson/migration:local')
    (EVIDENCE/(db.name+'-previous-BP1.log')).write_text(out+'\n'+error)
def checksum(db):
    return db.sql("SELECT id||'|'||filename||'|'||md5sum FROM commerce.databasechangelog WHERE id<>'commerce-20261010-ngn-001' ORDER BY orderexecuted")
def probe(db,classes,guard=False):
    out,error=docker('run','--rm','--network',db.network,'--read-only','--tmpfs','/tmp','-v',str(ROOT)+':/app/source:ro','-v',str(classes)+':/app/check:ro','-e','MIGRATION_PASSWORD=mock-commerce-migration','--entrypoint','java','nigson/migration:local','-cp','/app/check:/app/lib/*','NigeriaMigrationProbe',*(['guard'] if guard else []))
    (EVIDENCE/(db.name+('-guard' if guard else '-rollback-reapply')+'.log')).write_text(out+'\n'+error)
def main():
    assert os.environ.get('ENV','test')=='test'
    assert json.loads(docker('context','inspect')[0])[0]['Endpoints']['docker']['Host'].startswith('unix://')
    if os.environ.get('DOCKER_HOST'):assert os.environ['DOCKER_HOST'].startswith('unix://')
    classes=ROOT/'.build/ngn001/probe';classes.mkdir(parents=True,exist_ok=True)
    libraries=':'.join(str(p) for p in (ROOT/'.build/m2').rglob('*.jar'))
    subprocess.run([os.environ['JAVA_HOME']+'/bin/javac','-proc:none','-cp',libraries,'-d',str(classes),str(ROOT/'scripts/nigeria/NigeriaMigrationProbe.java')],check=True)
    before=preserved();network='nigson-test-ngn-'+uuid.uuid4().hex[:10];docker('network','create',network);db=None
    try:
        db=Database(network,'pristine');db.migrate('commerce',1);assert db.sql("SELECT id::text FROM commerce.currency WHERE code='NGN'; SELECT id::text FROM commerce.market WHERE code='NG'; SELECT id::text FROM commerce.market_destination WHERE country_code='NG'").splitlines()==[IDS['currency-NGN'],IDS['market-NG'],IDS['destination-NG']];shape=db.fingerprint();hashes=checksum(db);probe(db,classes);assert shape==db.fingerprint();assert hashes==checksum(db);db.migrate('commerce',1)
        db.sql("UPDATE commerce.market SET enabled=true WHERE code='NG'",'nigson_commerce_app');probe(db,classes,True);docker('rm','-f','-v',db.name);db=None
        db=Database(network,'previous');previous(db);hashes=checksum(db)
        currency,market,destination=[str(uuid.uuid4()) for _ in range(3)]
        db.sql(f"INSERT INTO commerce.currency(id,created_by,updated_by,code,minor_unit,active) VALUES ('{currency}','test','test','NGN',2,true); INSERT INTO commerce.market(id,created_by,updated_by,code,name,currency_code,enabled,policy_revision,tax_policy,returns_policy_revision,version) VALUES ('{market}','test','test','NG','Nigeria','NGN',true,7,'{{\"schemaVersion\":1,\"mode\":\"NONE\",\"rate\":\"0\",\"rounding\":\"HALF_UP\",\"approvalReference\":\"SYNTHETIC_ONLY\"}}','SYNTHETIC_ONLY',3); INSERT INTO commerce.market_destination(id,created_by,updated_by,market_id,country_code,address_rules,delivery_enabled) VALUES ('{destination}','test','test','{market}','NG','{{\"schemaVersion\":1,\"requiredFields\":[],\"postalPattern\":null,\"regionCodes\":[]}}',true)")
        state=db.sql("SELECT row_to_json(m) FROM commerce.market m WHERE code='NG'; SELECT row_to_json(d) FROM commerce.market_destination d WHERE country_code='NG'; SELECT row_to_json(c) FROM commerce.currency c WHERE code='NGN'")
        db.migrate('commerce',1);db.migrate('commerce',1);assert hashes==checksum(db);assert state==db.sql("SELECT row_to_json(m) FROM commerce.market m WHERE code='NG'; SELECT row_to_json(d) FROM commerce.market_destination d WHERE country_code='NG'; SELECT row_to_json(c) FROM commerce.currency c WHERE code='NGN'")
        docker('rm','-f','-v',db.name);db=None
        for case in ['previous-inactive-approved','wrong-minor-unit','wrong-market-currency']:
            db=Database(network,case);previous(db);hashes=checksum(db)
            if case=='previous-inactive-approved':
                prior=str(uuid.uuid4());db.sql(f"INSERT INTO commerce.currency(id,created_by,updated_by,code,minor_unit,active,version) VALUES ('{prior}','test','test','NGN',2,false,5)")
                db.migrate('commerce',1);assert hashes==checksum(db);assert db.sql("SELECT id::text||':'||minor_unit::text||':'||active::text||':'||version::text FROM commerce.currency WHERE code='NGN'")==prior+':2:true:6';db.migrate('commerce',1);assert db.sql("SELECT version FROM commerce.currency WHERE code='NGN'")=='6'
                docker('rm','-f','-v',db.name);db=None;continue
            if case=='wrong-minor-unit':db.sql(f"INSERT INTO commerce.currency(id,created_by,updated_by,code,minor_unit) VALUES ('{uuid.uuid4()}','test','test','NGN',3)")
            else:db.sql(f"INSERT INTO commerce.currency(id,created_by,updated_by,code,minor_unit) VALUES ('{uuid.uuid4()}','test','test','USD',2); INSERT INTO commerce.market(id,created_by,updated_by,code,name,currency_code) VALUES ('{uuid.uuid4()}','test','test','NG','Synthetic conflicting market','USD')")
            state=db.sql('SELECT row_to_json(c) FROM commerce.currency c ORDER BY code; SELECT row_to_json(m) FROM commerce.market m ORDER BY code')
            db.migrate('commerce',1,expected=False);assert hashes==checksum(db);assert state==db.sql('SELECT row_to_json(c) FROM commerce.currency c ORDER BY code; SELECT row_to_json(m) FROM commerce.market m ORDER BY code')
            docker('rm','-f','-v',db.name);db=None
        result={'test':'TEST-NGN-MARKET','result':'PASS','checks':['fresh deterministic approved disabled metadata','actual Liquibase last-change rollback/reapply','populated metadata rollback guard preserves data','previous BP1 checksums and explicit existing policy/IDs preserved on upgrade/repeat','previous supported inactive NGN activates only already-approved currency facts, preserves ID and increments version once','wrong NGN minor unit and wrong NG currency fail atomically','original local Docker state preserved'],'ids':IDS}
        (EVIDENCE/'acceptance.json').write_text(json.dumps(result,indent=2)+'\n');print(json.dumps(result,indent=2))
    finally:
        if db:docker('rm','-f','-v',db.name)
        docker('network','rm',network);assert before==preserved(),'Original local state changed'
if __name__=='__main__':main()
