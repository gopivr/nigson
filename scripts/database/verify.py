#!/usr/bin/env python3
"""Real PostgreSQL DATA-001 qualification, isolated from all local state.

Uses the production Liquibase image, one PostgreSQL 18 / pgvector database,
all four owner roles, incremental and direct release paths. No published ports.
"""
import json
import os
from pathlib import Path
import re
import subprocess
import time
import uuid

from catalog import ROOT, catalog
from generate import OWNERS, SESSION_TABLES, column, identifier, predicate

IMAGE = 'pgvector/pgvector:0.8.2-pg18@sha256:42e7f6b4e1eceb02ff14e3e6bc6108bbe259abbe83879dc1845d0da1ddeb555d'
EVIDENCE = ROOT / 'docs/implementation/evidence/2026-10-06-DATA-001'


def docker(*args, input=None, expected=True):
    result = subprocess.run(['docker', *args], input=input, text=True, capture_output=True, timeout=240)
    if (result.returncode == 0) != expected:
        raise AssertionError('Docker check failed: ' + ' '.join(args[:5]) + '\n' + result.stdout + result.stderr)
    return result.stdout.strip(), result.stderr.strip()


def preserved():
    ids = docker('ps', '-aq', '--filter', 'label=com.docker.compose.project=nigson-local')[0].split()
    containers = json.loads(docker('inspect', *ids)[0]) if ids else []
    return {'containers': {c['Id']: {'started': c['State']['StartedAt'], 'mounts': sorted(c['Mounts'], key=lambda m:m['Destination'])} for c in containers},
            'volumes': sorted(docker('volume', 'ls', '--filter', 'label=com.docker.compose.project=nigson-local', '--format', '{{.Name}}')[0].split())}


class Database:
    def __init__(self, network, suffix):
        self.network = network
        self.name = network + '-' + suffix
        docker('run', '-d', '--name', self.name, '--network', network, '--network-alias', 'postgres',
               '-e', 'POSTGRES_USER=nigson_bootstrap', '-e', 'POSTGRES_PASSWORD=mock-bootstrap',
               '-e', 'POSTGRES_DB=nigson', '--label', 'nigson.test=DATA-001', IMAGE)
        deadline = time.monotonic() + 60
        while True:
            try:
                self.sql('SELECT 1')
                break
            except AssertionError:
                assert time.monotonic() < deadline, 'Database startup timeout'
                time.sleep(1)
        if suffix == 'fresh':
            provision = subprocess.run(['terraform', '-chdir=' + str(ROOT / 'infrastructure/terraform/database'), 'output', '-raw', 'bootstrap_sql'], check=True, capture_output=True, text=True).stdout
            variables = []
            for owner in OWNERS:
                variables += ['-v', f'nigson_{owner}_migrator_password=mock-{owner}-migration',
                              '-v', f'nigson_{owner}_app_password=mock-{owner}-runtime']
            for _ in range(2):
                docker('exec', '-i', self.name, 'psql', '-X', '-U', 'nigson_bootstrap', '-d', 'nigson',
                       '-v', 'ON_ERROR_STOP=1', *variables, '-f', '-', input=provision)
        else:
            self.sql((ROOT / 'infrastructure/docker/postgres/10-local-bootstrap.sql').read_text())
            self.sql('CREATE EXTENSION vector WITH SCHEMA ai')

    def sql(self, query, role='nigson_bootstrap', expected=True):
        return docker('exec', '-i', self.name, 'psql', '-X', '-U', role, '-d', 'nigson',
                      '-v', 'ON_ERROR_STOP=1', '-At', '-f', '-', input=query, expected=expected)[0]

    def migrate(self, owner, release, expected=True, foundation=False):
        baseline = []
        if foundation:
            path = 'database/changelog/ai/master.yaml' if owner=='ai' else f'services/{owner}/src/main/resources/db/changelog/master.yaml'
            baseline = ['-v', str(ROOT / f'tests/fixtures/database/found003/{owner}-master.yaml') + ':/app/source/' + path + ':ro']
        output, error = docker('run', '--rm', '--network', self.network, '--read-only', '--tmpfs', '/tmp',
                              '-v', str(ROOT) + ':/app/source:ro', *baseline, '-e', 'APP_ENV=test',
                              '-e', 'MIGRATION_OWNER=' + owner, '-e', 'MIGRATION_RELEASE=BP' + str(release),
                              '-e', 'MIGRATION_JDBC_URL=jdbc:postgresql://postgres:5432/nigson',
                              '-e', 'MIGRATION_PASSWORD=mock-' + owner + '-migration',
                              'nigson/migration:local', expected=expected)
        (EVIDENCE / (self.name + '-' + owner + ('-foundation' if foundation else '-bp' + str(release)) + ('-pass' if expected else '-denied') + '.log')).write_text(output + '\n' + error)

    def tables(self):
        return set(self.sql("SELECT schemaname||'.'||tablename FROM pg_tables WHERE schemaname IN ('commerce','experience','integration','ai') AND tablename NOT IN ('databasechangelog','databasechangeloglock') ORDER BY 1").splitlines())

    def fingerprint(self):
        # Catalog-independent shape used to compare fresh and incrementally upgraded databases.
        return self.sql("""SELECT json_build_object('table',n.nspname||'.'||c.relname,'column',a.attname,
            'type',format_type(a.atttypid,a.atttypmod),'nn',a.attnotnull,'generated',a.attgenerated,
            'default',pg_get_expr(d.adbin,d.adrelid)) FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid
            JOIN pg_namespace n ON n.oid=c.relnamespace LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
            WHERE n.nspname IN ('commerce','experience','integration','ai') AND c.relkind='r'
            AND c.relname NOT LIKE 'databasechangelog%' AND a.attnum>0 AND NOT a.attisdropped
            ORDER BY n.nspname,c.relname,a.attnum;
            SELECT n.nspname||'.'||c.relname||'|'||co.conname||'|'||pg_get_constraintdef(co.oid)
            FROM pg_constraint co JOIN pg_class c ON c.oid=co.conrelid JOIN pg_namespace n ON n.oid=c.relnamespace
            WHERE n.nspname IN ('commerce','experience','integration','ai') AND c.relname NOT LIKE 'databasechangelog%'
            ORDER BY 1;
            SELECT schemaname||'.'||tablename||'|'||indexname||'|'||indexdef FROM pg_indexes
            WHERE schemaname IN ('commerce','experience','integration','ai') AND tablename NOT LIKE 'databasechangelog%'
            ORDER BY 1;""")

    def verify_catalog(self):
        tables = catalog()
        assert self.tables() == set(tables), 'Declared table inventory mismatch'
        actual = [json.loads(row) for row in self.sql("""SELECT json_build_object('table',n.nspname||'.'||c.relname,
            'column',a.attname,'type',format_type(a.atttypid,a.atttypmod),'nn',a.attnotnull,'default',pg_get_expr(d.adbin,d.adrelid))
            FROM pg_attribute a JOIN pg_class c ON c.oid=a.attrelid JOIN pg_namespace n ON n.oid=c.relnamespace
            LEFT JOIN pg_attrdef d ON d.adrelid=c.oid AND d.adnum=a.attnum
            WHERE n.nspname IN ('commerce','experience','integration','ai') AND c.relkind='r'
            AND c.relname NOT LIKE 'databasechangelog%' AND a.attnum>0 AND NOT a.attisdropped ORDER BY 1::text""").splitlines()]
        columns = {(row['table'], row['column']): row for row in actual}
        for name, table in tables.items():
            expected_columns = {f['name'] for f in table['fields']}
            if name in ('commerce.product', 'ai.knowledge_chunk'):
                expected_columns.add('search_vector')
            assert {r['column'] for r in actual if r['table'] == name} == expected_columns, name
            for field in table['fields']:
                row = columns[(name, field['name'])]
                datatype = field['type'].replace('varchar', 'character varying').replace('timestamptz', 'timestamp with time zone')
                datatype = re.sub(r'^char\(', 'character(', datatype)
                assert row['type'].replace('ai.', '') == datatype, (name, field, row)
                assert row['nn'] == (not field['nullable']), (name, field, row)
                assert (row['default'] is None) == (field['default'] is None), (name, field, row)
                if field['foreign']:
                    target = field['foreign'].split('.')
                    count = self.sql("SELECT count(*) FROM pg_constraint WHERE conrelid='" + name + "'::regclass AND contype='f' AND conname='" + identifier(name.split('.')[1] + '_' + field['name'] + '_fk') + "' AND confrelid='" + table['owner'] + '.' + target[0] + "'::regclass AND convalidated")
                    if name not in SESSION_TABLES:
                        assert count == '1', (name, field)
            app = 'nigson_' + table['owner'] + '_app'
            assert self.sql("SELECT has_table_privilege('" + app + "','" + name + "','SELECT,INSERT')") == 't'
            mutable = table['mutation'] != 'append-only'
            for privilege in ('UPDATE', 'DELETE'):
                assert self.sql("SELECT has_table_privilege('" + app + "','" + name + "','" + privilege + "')") == ('t' if mutable else 'f'), name
        assert self.sql("SELECT count(*) FROM pg_constraint c JOIN pg_class a ON a.oid=c.conrelid JOIN pg_class b ON b.oid=c.confrelid WHERE c.contype='f' AND a.relnamespace<>b.relnamespace") == '0'
        assert self.sql("SELECT count(*) FROM pg_constraint WHERE connamespace IN ('commerce'::regnamespace,'experience'::regnamespace,'integration'::regnamespace,'ai'::regnamespace) AND NOT convalidated") == '0'
        # Let PostgreSQL normalize the normative CHECK/default/index expressions.
        # Compare actual catalog definitions rather than merely counting objects.
        comparisons = []
        for name, table in tables.items():
            if name in SESSION_TABLES:
                continue  # upstream schema is exercised by the session checks
            short = name.split('.')[1]
            comparisons.append('CREATE TEMP TABLE expected (' + ','.join(column(f) for f in table['fields']) + ');')
            for field in table['fields']:
                comparisons.append(f"SELECT (SELECT pg_get_expr(d.adbin,d.adrelid) FROM pg_attrdef d JOIN pg_attribute a ON a.attrelid=d.adrelid AND a.attnum=d.adnum WHERE d.adrelid='{name}'::regclass AND a.attname='{field['name']}') IS NOT DISTINCT FROM (SELECT pg_get_expr(d.adbin,d.adrelid) FROM pg_attrdef d JOIN pg_attribute a ON a.attrelid=d.adrelid AND a.attnum=d.adnum WHERE d.adrelid='pg_temp.expected'::regclass AND a.attname='{field['name']}');")
            checks = [(identifier(short+'_rule_'+str(i)+'_ck'), predicate(c.strip())) for i,c in enumerate([c for c in table['checks'].split(';') if c.strip() and not c.startswith('No additional')], 1)]
            checks += [(identifier(short+'_'+f['name']+'_nonnil_ck'), f"{f['name']} <> '00000000-0000-0000-0000-000000000000'::uuid") for f in table['fields'] if f['type']=='uuid']
            if any(f['name']=='version' for f in table['fields']):
                checks.append((identifier(short+'_version_ck'), 'version >= 0'))
            if name == 'commerce.inventory_movement':
                checks.append(('inventory_movement_type_ck', "movement_type IN ('RESERVE','SETTLE','RELEASE','DISPATCH','RETURN','ADJUST')"))
            for constraint, expression in checks:
                comparisons.append('ALTER TABLE pg_temp.expected ADD CONSTRAINT expected_check CHECK ('+expression+');')
                comparisons.append(f"SELECT (SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='{name}'::regclass AND conname='{constraint}' AND convalidated) = (SELECT pg_get_constraintdef(oid) FROM pg_constraint WHERE conrelid='pg_temp.expected'::regclass AND conname='expected_check');")
                comparisons.append('ALTER TABLE pg_temp.expected DROP CONSTRAINT expected_check;')
            for generated in ('search_vector',) if name in ('commerce.product','ai.knowledge_chunk') else ():
                comparisons.append('ALTER TABLE pg_temp.expected ADD COLUMN '+generated+' tsvector;')
            for key in table['keys'].split(';'):
                kind, expression, where = re.fullmatch(r'(UQ|IX|GIN|HNSW)\((.*?)\)(?: WHERE (.*))?',key.strip()).groups()
                slug = re.sub(r'[^a-z0-9]+','_',expression.lower()).strip('_')
                index = identifier(short+'_'+slug+('_uq' if kind=='UQ' else '_ix'))
                operator = {'GIN':'gin','HNSW':'hnsw'}.get(kind,'btree')
                expression = expression.replace('vector_cosine_ops','ai.vector_cosine_ops')
                extra = ' WITH (m=16, ef_construction=64)' if kind=='HNSW' else ''
                comparisons.append(f"CREATE {'UNIQUE ' if kind=='UQ' else ''}INDEX expected_index ON pg_temp.expected USING {operator} ({expression}){extra}"+(' WHERE '+predicate(where) if where else '')+';')
                comparisons.append(f"SELECT substring(pg_get_indexdef('{table['owner']}.{index}'::regclass) FROM 'USING.*') = substring(pg_get_indexdef('pg_temp.expected_index'::regclass) FROM 'USING.*');")
                comparisons.append('DROP INDEX pg_temp.expected_index;')
            comparisons.append('DROP TABLE pg_temp.expected;')
        results = self.sql('\n'.join(comparisons)).splitlines()
        assert all(r=='t' or r.startswith(('CREATE','ALTER','DROP')) for r in results), 'Normative expression/index/default mismatch'
        (EVIDENCE / 'schema-fingerprint.txt').write_text(self.fingerprint())

    def ownership(self):
        for owner in OWNERS:
            app = 'nigson_' + owner + '_app'
            audit = 'nigson_' + owner + '_audit'
            self.sql('CREATE TABLE ' + owner + '.forbidden(id int)', app, False)
            self.sql('SELECT * FROM ' + owner + '.feature_configuration', app)
            for other in OWNERS:
                if other == owner:
                    continue
                self.sql('SELECT * FROM ' + other + '.audit_record', app, False)
                self.sql('DELETE FROM ' + other + '.feature_configuration', app, False)
                self.sql('CREATE TABLE ' + owner + '.forbidden_fk (id uuid REFERENCES ' + other + '.audit_record(id))', 'nigson_' + owner + '_migrator', False)
            self.sql('SET ROLE ' + audit + '; SELECT * FROM ' + owner + '.audit_record')
            self.sql('SET ROLE ' + audit + '; SELECT * FROM ' + owner + '.feature_configuration', expected=False)
            self.sql('SET ROLE ' + audit + '; DELETE FROM ' + owner + '.audit_record', expected=False)
            self.sql('SET ROLE ' + audit, app, False)
            self.sql('UPDATE ' + owner + '.audit_record SET action=action', app, False)
            self.sql('DELETE FROM ' + owner + '.audit_record', app, False)
            id = str(uuid.uuid4())
            self.sql(f"INSERT INTO {owner}.feature_configuration(id,created_by,updated_by,key,revision,value) VALUES ('{id}','test','test','offline-qualification',1,'{{}}'); UPDATE {owner}.feature_configuration SET revision=2,version=1 WHERE id='{id}'; DELETE FROM {owner}.feature_configuration WHERE id='{id}';", app)
            self.sql(f"INSERT INTO {owner}.audit_record(id,created_by,actor_subject,actor_kind,action,resource_type,resource_id,occurred_at,correlation_id) VALUES ('{uuid.uuid4()}','test','test','SYSTEM','QUALIFY','fixture','{id}',now(),'{uuid.uuid4()}')", app)

    def constraints(self):
        actor = "'data-test'"
        party = str(uuid.uuid4())
        def insert_party(id=party, subject='fixture', extra=''):
            return f"INSERT INTO commerce.party(id,created_by,updated_by,identity_subject,identity_issuer,email,display_name{extra}) VALUES ('{id}',{actor},{actor},'{subject}','https://identity.example.invalid','fixture@example.invalid','Fixture'"
        self.sql(insert_party() + ')', 'nigson_commerce_app')
        self.sql(insert_party(str(uuid.uuid4())) + ')', 'nigson_commerce_app', False)  # identity uniqueness
        self.sql(insert_party('00000000-0000-0000-0000-000000000000', 'nil') + ')', 'nigson_commerce_app', False)
        self.sql(insert_party(str(uuid.uuid4()), 'bad-status', ',status') + ",'UNKNOWN')", 'nigson_commerce_app', False)
        self.sql(insert_party(str(uuid.uuid4()), 'bad-version', ',version') + ',-1)', 'nigson_commerce_app', False)
        self.sql("UPDATE commerce.party SET display_name=repeat('x',161) WHERE id='" + party + "'", 'nigson_commerce_app', False)
        self.sql("INSERT INTO commerce.customer_profile(id,created_by,updated_by,party_id) VALUES ('" + str(uuid.uuid4()) + "','test','test','" + str(uuid.uuid4()) + "')", 'nigson_commerce_app', False)
        self.sql("UPDATE commerce.party SET display_name='Changed',version=version+1 WHERE id='" + party + "'", 'nigson_commerce_app')
        self.sql("DELETE FROM commerce.party WHERE id='" + party + "'", 'nigson_commerce_app')
        # Tracking checksums are immutable to applications.
        self.sql('UPDATE commerce.databasechangelog SET md5sum=NULL', 'nigson_commerce_app', False)
        assert self.sql("SELECT count(*) FROM pg_indexes WHERE schemaname='ai' AND indexdef LIKE '%USING hnsw%' AND indexdef LIKE '%m=%16%' ") == '1'
        # Real FK/cascade and CHECK behaviour, independent of business fixtures.
        sid = str(uuid.uuid4())
        self.sql(f"INSERT INTO commerce.spring_session VALUES ('{sid}','{sid}',0,0,60,60000,'test'); INSERT INTO commerce.spring_session_attributes VALUES ('{sid}','fixture',decode('01','hex')); DELETE FROM commerce.spring_session WHERE primary_id='{sid}'", 'nigson_commerce_app')
        assert self.sql('SELECT count(*) FROM commerce.spring_session_attributes') == '0'
        self.sql(f"INSERT INTO commerce.spring_session VALUES ('{sid}','{sid}',0,0,0,0,'test')", 'nigson_commerce_app', False)

    def commerce_constraints(self):
        """Financial/stock/NULL-aware uniqueness against real owner tables."""
        def literal(value):
            return "'" + str(value).replace("'", "''") + "'"
        def row(table, values, expected=True):
            fields = {f['name'] for f in catalog()['commerce.' + table]['fields']}
            values = {'id': str(uuid.uuid4()), 'created_by': 'test', **values}
            if 'updated_by' in fields:
                values['updated_by'] = 'test'
            self.sql('INSERT INTO commerce.' + table + '(' + ','.join(values) + ') VALUES (' + ','.join(literal(v) for v in values.values()) + ')', 'nigson_commerce_app', expected)
            return values['id']
        party = row('party', {'identity_subject':'financial-fixture','identity_issuer':'https://identity.example.invalid','email':'fixture@example.invalid','display_name':'Fixture'})
        assert self.sql("SELECT minor_unit FROM commerce.currency WHERE code='NGN'") == '2', 'Approved NGN2 baseline missing'
        market = row('market', {'code':'OFFLINE_TEST','name':'Offline test, disabled','currency_code':'NGN'})
        cart = row('cart', {'party_id':party,'market_id':market,'last_activity_at':'2026-10-06T00:00:00Z'})
        row('cart', {'party_id':party,'market_id':market,'last_activity_at':'2026-10-06T00:00:00Z'}, False)
        row('cart', {'party_id':party,'market_id':market,'last_activity_at':'2026-10-06T00:00:00Z','status':'EXPIRED'})
        product = row('product', {'name':'Offline test','slug':'offline-test','description':'Test draft'})
        row('product', {'name':'Invalid JSON','slug':'invalid-json','description':'Test draft','specifications':'[]'}, False)
        sku = row('sku', {'product_id':product,'code':'OFFLINE_TEST','variant_name':'Test','unit':'unit','active':'false'})
        location = row('stock_location', {'code':'OFFLINE_TEST','name':'Test location','active':'false'})
        balance = {'sku_id':sku,'location_id':location,'on_hand':10,'reserved':3,'allocated':2}
        row('inventory_balance', balance)
        row('inventory_balance', balance, False)
        for changes in ({'on_hand':-1},{'reserved':-1},{'allocated':-1},{'reserved':9}):
            self.sql('UPDATE commerce.inventory_balance SET ' + ','.join(k+'='+str(v) for k,v in changes.items()) + " WHERE sku_id='"+sku+"'", 'nigson_commerce_app', False)
        carrier = row('carrier_quote', {'cart_id':cart,'cart_version':0,'market_id':market,'provider':'FEDEX','mode':'MOCK','service_code':'OFFLINE_TEST','currency_code':'NGN','delivery_amount':0,'address_hash':'0'*64,'package_hash':'1'*64,'package_snapshot':'{}','provider_snapshot':'{}','expires_at':'2026-10-07T00:00:00Z','status':'QUOTED'})
        order_values = {'order_number':'OFFLINE_TEST','party_id':party,'market_id':market,'status':'PENDING','payment_status':'UNPAID','fulfillment_status':'UNFULFILLED','currency_code':'NGN','subtotal':10,'discount_total':0,'tax_total':0,'delivery_total':0,'grand_total':10,'address_snapshot':'{}','commercial_snapshot':'{}','carrier_quote_id':carrier,'carrier_snapshot':'{}'}
        row('sales_order', {**order_values,'grand_total':11}, False)
        row('sales_order', {**order_values,'refunded_total':11}, False)
        order = row('sales_order', order_values)
        payment = {'order_id':order,'provider':'PAYSTACK','reference':'offline-reference','merchant_account':'offline-dummy','expected_amount':10,'currency_code':'NGN','state':'UNKNOWN'}
        row('payment_attempt', payment)
        row('payment_attempt', {**payment,'reference':'another-reference','state':'PENDING'}, False)
        row('payment_attempt', {**payment,'reference':'historical-reference','state':'FAILED'})
        row('payment_attempt', {**payment,'reference':'negative-amount','state':'FAILED','expected_amount':-1}, False)
        self.sql("DELETE FROM commerce.party WHERE id='"+party+"'", 'nigson_commerce_app', False)


def main():
    assert os.environ.get('ENV', 'test') == 'test', 'Qualification requires ENV=test'
    if os.environ.get('DOCKER_HOST') and not os.environ['DOCKER_HOST'].startswith('unix://'):
        raise ValueError('Qualification refuses remote Docker hosts')
    context = json.loads(docker('context', 'inspect')[0])[0]
    assert context['Endpoints']['docker']['Host'].startswith('unix://'), 'Qualification requires a local Docker endpoint'
    before = preserved()
    EVIDENCE.mkdir(parents=True, exist_ok=True)
    (EVIDENCE / 'local-state-before.json').write_text(json.dumps(before, indent=2)+'\n')
    report = {'test': 'TEST-DATA-MIGRATE / TEST-DATA-OWNERSHIP', 'result': 'FAIL', 'checks': []}
    networks = []
    databases = []
    try:
        for path in ('incremental', 'fresh'):
            network = 'nigson-test-data-' + uuid.uuid4().hex[:10]
            networks.append(network)
            docker('network', 'create', network)
            db = Database(network, path)
            databases.append(db)
            if path == 'incremental':
                for owner in OWNERS:
                    db.migrate(owner, 1, foundation=True)
                assert db.tables() == SESSION_TABLES, 'Foundation baseline differs from accepted FOUND-003'
                report['checks'].append('Accepted FOUND-003 masters applied before DATA-001 upgrades')
            phases = range(1, 6) if path == 'incremental' else (5,)
            for phase in phases:
                for owner in OWNERS:
                    db.migrate(owner, phase)
                expected = {n for n,t in catalog().items() if t['phase'] <= phase}
                assert db.tables() == expected, (phase, db.tables() ^ expected)
                first = db.fingerprint()
                for owner in OWNERS:
                    db.migrate(owner, phase)
                assert db.fingerprint() == first, 'Repeat migration changed schema'
                report['checks'].append(path + ' BP' + str(phase) + ': production masters applied/repeated; exact phase table inventory')
            db.verify_catalog()
            db.ownership()
            shape = db.fingerprint()
            if path == 'incremental':
                baseline = shape
            else:
                assert baseline == shape, 'Fresh/incremental schema mismatch'
            db.constraints()
            db.commerce_constraints()
            for owner in OWNERS:
                db.migrate(owner, 1, expected=False)
            report['checks'].append(path + ': catalog fields/types/nullability/default presence/FKs/privileges; cross-owner SELECT/write/FK and runtime DDL denied; append-only/audit roles; invalid/duplicate rows; downgrade refused')
        report['result'] = 'PASS'
    finally:
        for db in databases:
            docker('rm', '-f', '-v', db.name)
        for network in networks:
            docker('network', 'rm', network)
        after = preserved()
        (EVIDENCE / 'local-state-after.json').write_text(json.dumps(after, indent=2)+'\n')
        report['local_state_preserved'] = before == after
        if before != after:
            report['result'] = 'FAIL'
        (EVIDENCE / 'acceptance.json').write_text(json.dumps(report, indent=2) + '\n')
        assert before == after, 'Original nigson-local state changed'
    print(json.dumps(report, indent=2))


if __name__ == '__main__':
    main()
