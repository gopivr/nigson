"""Upgrade old release wrappers, preserve checksums/booking evidence, qualify additive rollback."""
from pathlib import Path
import sys, subprocess, tempfile, json, uuid, time, os
ROOT=Path(__file__).resolve().parents[2]
sys.path.insert(0,str(ROOT/'scripts/database'))
import verify as database_verification
from verify import Database,docker,preserved,IMAGE
database_verification.EVIDENCE=ROOT/'.build/order-cancellation/migration-evidence'
database_verification.EVIDENCE.mkdir(parents=True,exist_ok=True)

def main():
 if os.environ.get('ENV','test')!='test':raise ValueError('TEST_ONLY')
 before=preserved();network='nigson-test-cancel-migration-'+uuid.uuid4().hex[:8]
 docker('network','create',network)
 db=Database.__new__(Database);db.network=network;db.name=network+'-postgres'
 try:
  docker('run','-d','--name',db.name,'--network',network,'--network-alias','postgres','-e','POSTGRES_USER=nigson_bootstrap','-e','POSTGRES_PASSWORD=mock-bootstrap','-e','POSTGRES_DB=nigson',IMAGE)
  for _ in range(60):
   try:db.sql('SELECT 1');break
   except AssertionError:time.sleep(1)
  db.sql((ROOT/'infrastructure/docker/postgres/10-local-bootstrap.sql').read_text())
  with tempfile.TemporaryDirectory(prefix='nigson-cancellation-migrations-') as directory:
   for owner in ('commerce','integration'):
    relative=f'services/{owner}/src/main/resources/db/changelog/releases/BP1.yaml'
    old=Path(directory)/(owner+'.yaml');old.write_bytes(subprocess.check_output(['git','show','HEAD:'+relative],cwd=ROOT))
    docker('run','--rm','--network',network,'--read-only','--tmpfs','/tmp','-v',str(ROOT)+':/app/source:ro','-v',str(old)+':/app/source/'+relative+':ro','-e','APP_ENV=test','-e','MIGRATION_OWNER='+owner,'-e','MIGRATION_RELEASE=BP1','-e','MIGRATION_JDBC_URL=jdbc:postgresql://postgres:5432/nigson','-e','MIGRATION_PASSWORD=mock-'+owner+'-migration','nigson/migration:local')
   checks={owner:db.sql(f'SELECT id,md5sum FROM {owner}.databasechangelog ORDER BY id') for owner in ('commerce','integration')}
   booking=str(uuid.uuid4());shipment=str(uuid.uuid4())
   db.sql(f"INSERT INTO integration.carrier_booking(id,created_by,updated_by,shipment_id,business_key,provider,mode,state,request_hash,provider_shipment_id) VALUES ('{booking}','test','test','{shipment}','upgrade-{booking}','PANDAGO','MOCK','BOOKED','{'1'*64}','existing-panda-order')",'nigson_integration_app')
   for owner in ('commerce','integration'):
    db.migrate(owner,1);db.migrate(owner,1)
    current=set(db.sql(f'SELECT id,md5sum FROM {owner}.databasechangelog ORDER BY id').splitlines())
    assert set(checks[owner].splitlines())<=current,'Applied checksums changed'
    assert db.sql(f"SELECT character_maximum_length FROM information_schema.columns WHERE table_schema='{owner}' AND table_name='carrier_cancellation' AND column_name='reference'")=='160'
   assert db.sql(f"SELECT provider_shipment_id FROM integration.carrier_booking WHERE id='{booking}'")=='existing-panda-order'
   assert db.sql("SELECT has_table_privilege('nigson_commerce_app','commerce.order_cancellation','SELECT,INSERT,UPDATE,DELETE')")=='t'
   shape=db.fingerprint()
   for owner,stems in [('commerce',['037-bp1-cancellation-reference','036-bp1-order-cancellation']),('integration',['027-pandago-cancellation'])]:
    base=ROOT/f'services/{owner}/src/main/resources/db/changelog'
    for stem in stems:db.sql((base/(stem+'-rollback.sql')).read_text(),'nigson_'+owner+'_migrator')
    for stem in reversed(stems):db.sql((base/(stem+'.sql')).read_text(),'nigson_'+owner+'_migrator')
   assert db.fingerprint()==shape,'Empty rollback/reapply changed schema'
   db.sql(f"INSERT INTO integration.carrier_cancellation(id,created_by,updated_by,source_request_id,booking_id,shipment_id,reference,mode,account_hash,request_hash,generation,requested_at,piece_numbers) VALUES ('{uuid.uuid4()}','test','test','{uuid.uuid4()}','{booking}','{shipment}','oldreference123','MOCK','{'1'*64}','{'2'*64}','{uuid.uuid4()}',now(),'[]'::jsonb)",'nigson_integration_app')
   db.sql((ROOT/'services/integration/src/main/resources/db/changelog/027-pandago-cancellation-rollback.sql').read_text(),'nigson_integration_migrator',False)
   assert db.fingerprint()==shape,'Populated refusal changed schema'
   print('PASS: previous BP1 upgrade/repeat; unchanged old checksums and booking; owner grants; empty rollback/reapply; populated Integration refusal')
 finally:
  docker('rm','-f','-v',db.name);docker('network','rm',network)
  assert before==preserved(),'Original local state changed'
if __name__=='__main__':main()
