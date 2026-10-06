#!/usr/bin/env python3
"""Qualify Liquibase rollback/reapply and populated-evidence guards offline.

Run after make verify and make database-check with the pinned JAVA_HOME.
Only local Docker and fresh, unpublished PostgreSQL test containers are used.
"""
import json
import os
from pathlib import Path
import shutil
import subprocess
import uuid

from verify import Database, docker, EVIDENCE, preserved, OWNERS, ROOT, catalog


def main():
    assert os.environ.get('ENV', 'test') == 'test', 'Rollback qualification requires ENV=test'
    if os.environ.get('DOCKER_HOST') and not os.environ['DOCKER_HOST'].startswith('unix://'):
        raise ValueError('Rollback qualification refuses remote Docker hosts')
    assert json.loads(docker('context', 'inspect')[0])[0]['Endpoints']['docker']['Host'].startswith('unix://')
    javac = str(Path(os.environ['JAVA_HOME']) / 'bin/javac') if os.environ.get('JAVA_HOME') else shutil.which('javac')
    assert javac, 'Pinned Java 25 compiler required'
    version = subprocess.run([javac, '-version'], check=True, text=True, capture_output=True)
    assert '25.0.2' in version.stdout + version.stderr, 'Use the repository-pinned Java 25.0.2 compiler'
    libraries = ':'.join(str(p) for p in (ROOT / '.build/m2').rglob('*.jar'))
    assert libraries, 'Run make verify to resolve the locked Java dependencies first'
    classes = ROOT / '.build/database-rollback'
    classes.mkdir(parents=True, exist_ok=True)
    subprocess.run([javac, '-proc:none', '-cp', libraries, '-d', str(classes), str(ROOT / 'scripts/database/RollbackProbe.java')], check=True)
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    before = preserved()
    network = 'nigson-test-data-' + uuid.uuid4().hex[:10]
    docker('network', 'create', network)
    db = None
    try:
        db = Database(network, 'fresh')
        for owner in OWNERS:
            db.migrate(owner, 5)
        for owner in OWNERS:
            out, error = docker('run', '--rm', '--network', network, '--read-only', '--tmpfs', '/tmp',
                                '-v', str(ROOT) + ':/app/source:ro', '-v', str(classes) + ':/app/check:ro',
                                '-e', 'MIGRATION_OWNER=' + owner, '-e', 'MIGRATION_PASSWORD=mock-' + owner + '-migration',
                                '--entrypoint', 'java', 'nigson/migration:local', '-cp', '/app/check:/app/lib/*', 'RollbackProbe')
            (EVIDENCE / (owner + '-rollback-reapply.log')).write_text(out + '\n' + error)
        assert db.tables() == set(catalog())
        for owner in OWNERS:
            db.sql(f"INSERT INTO {owner}.audit_record(id,created_by,actor_subject,actor_kind,action,resource_type,resource_id,occurred_at,correlation_id) VALUES ('{uuid.uuid4()}','test','test','SYSTEM','TEST','fixture','{uuid.uuid4()}',now(),'{uuid.uuid4()}')", 'nigson_' + owner + '_app')
            folder = ROOT / ('database/changelog/ai' if owner == 'ai' else f'services/{owner}/src/main/resources/db/changelog')
            file = next(folder.glob('010-*-platform-tables-rollback.sql'))
            first = db.fingerprint()
            db.sql(file.read_text(), expected=False)
            assert first == db.fingerprint(), 'Populated rollback guard changed schema'
        report = {'test': 'TEST-DATA-ROLLBACK', 'result': 'PASS', 'checks': [
            'Actual Liquibase rollback of empty additive changes and complete BP5 reapply for all owners',
            'Populated audit/platform tables fail before any schema drop', 'Full catalog inventory restored',
            'Original local state preserved']}
        (EVIDENCE / 'rollback.json').write_text(json.dumps(report, indent=2) + '\n')
        print(json.dumps(report, indent=2))
    finally:
        if db:
            docker('rm', '-f', '-v', db.name)
        docker('network', 'rm', network)
        assert before == preserved(), 'Original local state changed'


if __name__ == '__main__':
    main()
