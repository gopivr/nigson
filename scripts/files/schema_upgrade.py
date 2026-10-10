"""Previous accepted BP1 -> additive files upgrade/rollback on isolated owner DB."""
from pathlib import Path
import json
import os
import subprocess
import sys
import uuid
ROOT = Path(__file__).resolve().parents[2]
sys.path.insert(0, str(ROOT / 'scripts/database'))
import verify


def main():
    evidence = ROOT / '.build/files001/upgrade-evidence'
    evidence.mkdir(parents=True, exist_ok=True)
    verify.EVIDENCE = evidence
    before = verify.preserved()
    network = 'nigson-test-file-upgrade-' + uuid.uuid4().hex[:10]
    verify.docker('network', 'create', network)
    db = None
    try:
        db = verify.Database(network, 'postgres')
        wrapper = ROOT / 'services/commerce/src/main/resources/db/changelog/releases/BP1.yaml'
        baseline = json.loads(wrapper.read_text())
        baseline['databaseChangeLog'] = [x for x in baseline['databaseChangeLog'] if '023-bp1-file-transfer' not in x['include']['file']]
        temporary = evidence / 'previous-BP1.yaml'
        temporary.write_text(json.dumps(baseline, indent=2) + '\n')
        output, error = verify.docker('run', '--rm', '--network', network, '--read-only', '--tmpfs', '/tmp', '-v', str(ROOT) + ':/app/source:ro', '-v', str(temporary) + ':/app/source/services/commerce/src/main/resources/db/changelog/releases/BP1.yaml:ro', '-e', 'APP_ENV=test', '-e', 'MIGRATION_OWNER=commerce', '-e', 'MIGRATION_RELEASE=BP1', '-e', 'MIGRATION_JDBC_URL=jdbc:postgresql://postgres:5432/nigson', '-e', 'MIGRATION_PASSWORD=mock-commerce-migration', 'nigson/migration:local')
        (evidence / 'previous-bp1.log').write_text(output + '\n' + error)
        assert db.sql("SELECT to_regclass('commerce.file_transfer') IS NULL") == 't'
        original = db.sql('SELECT id||md5sum FROM commerce.databasechangelog ORDER BY id')
        product = str(uuid.uuid4())
        db.sql("INSERT INTO commerce.product(id,created_by,updated_by,name,slug,description) VALUES ('" + product + "','synthetic-upgrade-marker','test','Synthetic upgrade marker','synthetic-" + product + "','Synthetic')", 'nigson_commerce_app')
        db.migrate('commerce', 1)
        assert db.sql("SELECT id||md5sum FROM commerce.databasechangelog WHERE id<>'commerce-20261010-files-001' ORDER BY id") == original
        assert db.sql("SELECT count(*) FROM commerce.product WHERE id='" + product + "'") == '1'
        first = db.fingerprint()
        db.migrate('commerce', 1)
        assert first == db.fingerprint()
        javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac')
        libraries = ':'.join(str(p) for p in (ROOT / '.build/m2').rglob('*.jar'))
        classes = evidence / 'classes'
        classes.mkdir(exist_ok=True)
        subprocess.run([javac, '-proc:none', '-cp', libraries, '-d', str(classes), str(ROOT / 'scripts/files/FileIncrementProbe.java')], check=True)
        output, error = verify.docker('run', '--rm', '--network', network, '--read-only', '--tmpfs', '/tmp', '-v', str(ROOT) + ':/app/source:ro', '-v', str(classes) + ':/app/check:ro', '-e', 'MIGRATION_PASSWORD=mock-commerce-migration', '--entrypoint', 'java', 'nigson/migration:local', '-cp', '/app/check:/app/lib/*', 'FileIncrementProbe')
        (evidence / 'file-increment-rollback-reapply.log').write_text(output + '\n' + error)
        assert first == db.fingerprint()
        party, asset = str(uuid.uuid4()), str(uuid.uuid4())
        db.sql("INSERT INTO commerce.party(id,created_by,updated_by,identity_issuer,identity_subject,email,display_name) VALUES ('"+party+"','test','test','https://identity.synthetic.invalid','"+party+"','"+party+"@example.invalid','Synthetic'); INSERT INTO commerce.asset_reference(id,created_by,updated_by,object_key,purpose,mime_type,byte_size,checksum_sha256,status) VALUES ('"+asset+"','test','test','synthetic-upgrade-key','BROCHURE','application/pdf',32,'"+'0'*64+"','UPLOADING'); INSERT INTO commerce.file_transfer(id,created_by,updated_by,owner_party_id,source_key,final_key,upload_expires_at) VALUES ('"+asset+"','test','test','"+party+"','synthetic-source','synthetic-target',now()+interval '5 minutes');", 'nigson_commerce_app')
        db.sql((ROOT / 'services/commerce/src/main/resources/db/changelog/023-bp1-file-transfer-rollback.sql').read_text(), expected=False)
        assert first == db.fingerprint()
        assert db.sql("SELECT count(*) FROM commerce.file_transfer WHERE id='"+asset+"'") == '1'
        result = {'result': 'PASS', 'checks': ['Previous BP1 without new increment applied', 'Existing marker product and all prior checksums preserved during upgrade', 'Repeated upgrade leaves schema unchanged', 'Actual Liquibase empty new-increment rollback/reapply preserves prior BP1 data', 'Populated transfer rollback refuses before any drop'], 'local_state_preserved': True}
        (evidence / 'acceptance.json').write_text(json.dumps(result, indent=2)+'\n')
        print(json.dumps(result))
    finally:
        if db is not None:
            verify.docker('rm', '-f', '-v', db.name)
        verify.docker('network', 'rm', network)
        assert before == verify.preserved()

if __name__ == '__main__':
    main()
