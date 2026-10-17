#!/usr/bin/env python3
"""Render reviewed, checked-in migration artifacts from the authoritative catalog.

Generation never connects to a database and never writes an applied bootstrap.
Use --check after review; deployed DDL is read from versioned SQL, not Markdown.
"""
import argparse
import hashlib
import json
from pathlib import Path
import re

from catalog import ROOT, catalog

OWNERS = ('commerce', 'experience', 'integration', 'ai')
ADDITIVE_TABLES = {
    'integration.notification_dispatch': ('024-bp1-notification-dispatch', 'integration-20261017-msg-001', 'nigson-messaging', 'ADR-018: additive owned immutable notification dispatch identity; prior migrations preserved.'),
    'commerce.file_transfer': ('023-bp1-file-transfer', 'commerce-20261010-files-001', 'nigson-files', 'ADR-009: additive owned file transfer intent; existing migrations preserved.'),
    'commerce.carrier_cancellation': ('025-bp1-carrier-cancellation', 'commerce-20261016-carrier-001', 'nigson-carrier', 'ADR-014: additive owned cancellation/dispatch exclusion; prior migrations preserved.'),
    'integration.carrier_cancellation': ('023-bp1-carrier-cancellation', 'integration-20261016-carrier-001', 'nigson-carrier', 'ADR-014: additive owned cancellation checkpoint; prior migrations preserved.'),
}
SESSION_TABLES = {'commerce.spring_session', 'commerce.spring_session_attributes'}


def identifier(name):
    if len(name) <= 63:
        return name
    return name[:54] + '_' + hashlib.sha256(name.encode()).hexdigest()[:8]


def predicate(value):
    value = re.sub(r'\bIN \(([^)]+)\)', lambda m: 'IN (' + ','.join(
        item if re.fullmatch(r'-?\d+', item.strip()) else "'" + item.strip() + "'"
        for item in m[1].split(',')) + ')', value)
    return re.sub(r'=\s*(ACTIVE|PENDING|FEDEX|WHATSAPP|object|array)\b', r"='\1'", value)


def column(field, force_not_null=False):
    datatype = 'ai.' + field['type'] if field['type'].startswith('vector(') else field['type']
    result = f"{field['name']} {datatype}" + (' NOT NULL' if (force_not_null or not field['nullable']) else '')
    default = field['default']
    if default is not None:
        if default in ('{}', '[]'):
            default = "'" + default + "'::jsonb"
        elif not re.fullmatch(r'-?\d+(?:\.\d+)?|true|false|now\(\)', default):
            default = "'" + default + "'"
        result += ' DEFAULT ' + default
    return result


def rollback_sql(owner, statements):
    """Explicit additive rollback; populated tables fail closed before any drop."""
    tables = [re.match(r'CREATE TABLE ([\w.]+)', s)[1] for s in statements if s.startswith('CREATE TABLE ')]
    checks = [f"IF EXISTS (SELECT 1 FROM {t}) THEN RAISE EXCEPTION 'Populated table {t}: use expand/contract migration'; END IF;" for t in tables]
    result = ['DO $$ BEGIN ' + ' '.join(checks) + ' END $$;'] if checks else []
    for statement in reversed(statements):
        if statement.startswith('CREATE TABLE '):
            result.append('DROP TABLE ' + re.match(r'CREATE TABLE ([\w.]+)', statement)[1] + ';')
        elif statement.startswith('CREATE ') and 'INDEX ' in statement:
            result.append('DROP INDEX ' + owner + '.' + re.search(r'INDEX (\w+)', statement)[1] + ';')
        elif statement.startswith('ALTER TABLE '):
            match = re.match(r'ALTER TABLE ([\w.]+) ADD (COLUMN|CONSTRAINT) (\w+)', statement)
            if not match:
                raise ValueError('Missing additive rollback: ' + statement)
            result.append(f'ALTER TABLE {match[1]} DROP {match[2]} {match[3]};')
    return '\n\n'.join(result) + '\n'


def generate():
    tables = catalog()
    files = {}
    manifest = {'specification': '1.1.0', 'tables': tables}
    for owner in OWNERS:
        base = Path('database/changelog/ai') if owner == 'ai' else Path(f'services/{owner}/src/main/resources/db/changelog')
        sequence = 10
        for phase in range(1, 6):
            selected = {name: table for name, table in tables.items() if table['owner'] == owner and table['phase'] == phase and name not in SESSION_TABLES and name not in ADDITIVE_TABLES}
            groups = []
            # Platform creation is its own increment (BP5 for AI), before domain creation.
            for category, subset in [('platform', {n:t for n,t in selected.items() if t['module']=='platform'}),
                                     ('domain', {n:t for n,t in selected.items() if t['module']!='platform'})]:
                statements = []
                for fullname, table in subset.items():
                    name = fullname.split('.')[1]
                    # Preserve the originally released BP1 table DDL. A later
                    # additive migration relaxes this one field for API-190.
                    fields = [column(field, (fullname == 'experience.lead' and field['name'] == 'email') or (fullname == 'experience.inbound_message' and field['name'] == 'sender_ciphertext')) for field in table['fields']]
                    primary = [field['name'] for field in table['fields'] if field['primary']]
                    fields.append(f"CONSTRAINT {identifier(name+'_id_pk')} PRIMARY KEY ({','.join(primary)})")
                    for field in table['fields']:
                        if field['name'] == 'version':
                            fields.append(f"CONSTRAINT {identifier(name+'_version_ck')} CHECK (version >= 0)")
                        if field['type'] == 'uuid':
                            fields.append(f"CONSTRAINT {identifier(name+'_'+field['name']+'_nonnil_ck')} CHECK ({field['name']} <> '00000000-0000-0000-0000-000000000000'::uuid)")
                    checks = [predicate(value.strip()) for value in table['checks'].split(';') if value.strip() and not value.startswith('No additional')]
                    for index, check in enumerate(checks, 1):
                        fields.append(f"CONSTRAINT {identifier(name+'_rule_'+str(index)+'_ck')} CHECK ({check})")
                    if fullname == 'commerce.inventory_movement':
                        fields.append("CONSTRAINT inventory_movement_type_ck CHECK (movement_type IN ('RESERVE','SETTLE','RELEASE','DISPATCH','RETURN','ADJUST'))")
                    statements.append(f"CREATE TABLE {fullname} (\n    " + ',\n    '.join(fields) + '\n);')
                    privileges = 'SELECT, INSERT' if table['mutation'] == 'append-only' else 'SELECT, INSERT, UPDATE, DELETE'
                    statements.append(f'GRANT {privileges} ON {fullname} TO nigson_{owner}_app;')
                    if name == 'audit_record':
                        statements.append(f'GRANT SELECT ON {fullname} TO nigson_{owner}_audit;')
                if statements:
                    groups.append((category+'-tables', statements))
            statements = []
            for fullname, table in selected.items():
                name = fullname.split('.')[1]
                if fullname == 'commerce.product':
                    statements.append("ALTER TABLE commerce.product ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, coalesce(name,'') || ' ' || coalesce(description,''))) STORED;")
                if fullname == 'ai.knowledge_chunk':
                    statements.append("ALTER TABLE ai.knowledge_chunk ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, coalesce(section_title,'') || ' ' || coalesce(body,''))) STORED;")
                for key in table['keys'].split(';'):
                    match = re.fullmatch(r'(UQ|IX|GIN|HNSW)\((.*?)\)(?: WHERE (.*))?', key.strip())
                    if not match:
                        raise ValueError('Unrecognized key notation: '+fullname+': '+key)
                    kind, expression, where = match.groups()
                    slug = re.sub(r'[^a-z0-9]+','_',expression.lower()).strip('_')
                    index_name = identifier(name+'_'+slug+('_uq' if kind == 'UQ' else '_ix'))
                    if kind == 'UQ' and not where and '(' not in expression:
                        statements.append(f'ALTER TABLE {fullname} ADD CONSTRAINT {index_name} UNIQUE ({expression});')
                    else:
                        operator = {'GIN':' USING gin','HNSW':' USING hnsw'}.get(kind,'')
                        if kind == 'HNSW':
                            expression = expression.replace('vector_cosine_ops','ai.vector_cosine_ops')
                        extra = ' WITH (m=16, ef_construction=64)' if kind == 'HNSW' else ''
                        statements.append(f"CREATE {'UNIQUE ' if kind=='UQ' else ''}INDEX {index_name} ON {fullname}{operator} ({expression}){extra}" + (' WHERE '+predicate(where) if where else '')+';')
                for field in table['fields']:
                    if field['foreign']:
                        statements.append(f"CREATE INDEX {identifier(name+'_'+field['name']+'_fk_ix')} ON {fullname} ({field['name']});")
                if fullname == 'commerce.cart':
                    statements.append("CREATE UNIQUE INDEX cart_active_context_uq ON commerce.cart (party_id,coalesce(organization_id,'00000000-0000-0000-0000-000000000000'::uuid),market_id) WHERE status='ACTIVE';")
                if fullname == 'commerce.sku':
                    statements.append('CREATE INDEX sku_product_id_active_ix ON commerce.sku (product_id,active);')
                if fullname == 'commerce.product_market_eligibility':
                    statements.append('CREATE INDEX product_market_eligibility_market_id_product_id_ix ON commerce.product_market_eligibility (market_id,product_id);')
            if statements:
                groups.append(('indexes', statements))
            statements = []
            for fullname, table in tables.items():
                if table['owner'] != owner or fullname in SESSION_TABLES or fullname in ADDITIVE_TABLES:
                    continue
                for field in table['fields']:
                    if not field['foreign']:
                        continue
                    target = field['foreign'].split('.')
                    target_table = owner+'.'+target[0]
                    target_field = target[1] if len(target)>1 else 'id'
                    if target_table not in tables:
                        raise ValueError('Unknown FK target: '+target_table)
                    if max(table['phase'],tables[target_table]['phase']) != phase:
                        continue
                    name = fullname.split('.')[1]
                    fk = identifier(name+'_'+field['name']+'_fk')
                    statements.append(f"ALTER TABLE {fullname} ADD CONSTRAINT {fk} FOREIGN KEY ({field['name']}) REFERENCES {target_table} ({target_field}) ON DELETE {'CASCADE' if field['cascade'] else 'RESTRICT'};")
            if statements:
                groups.append(('constraints', statements))
            includes = []
            for kind, statements in groups:
                sequence += 1
                number = 10 if kind == 'platform-tables' else (phase + 1) * 10 + {'indexes':1, 'constraints':2}.get(kind, 0)
                stem = f'{number:03d}-bp{phase}-{kind}'
                change = f'{owner}-20261006-{sequence:03d}'
                sqlpath = base/(stem+'.sql')
                files[sqlpath] = '-- DATA-001: specification 1.1.0; reviewed owner-local DDL.\n' + '\n\n'.join(statements)+'\n'
                # Table rollback refuses to remove business evidence. No automatic
                # rollback is exposed by the migration CLI or application startup.
                files[base/(stem+'-rollback.sql')] = rollback_sql(owner, statements)
                guard = f"SELECT CASE WHEN current_user='nigson_{owner}_migrator' AND current_setting('server_version_num')::int BETWEEN 180000 AND 189999"
                if owner == 'ai':
                    guard += " AND EXISTS (SELECT 1 FROM pg_extension WHERE extname='vector' AND extversion='0.8.2' AND extnamespace='ai'::regnamespace)"
                guard += " THEN 1 ELSE 0 END"
                payload = {'databaseChangeLog':[{'changeSet':{'id':change,'author':'nigson-platform','logicalFilePath':owner+'/'+stem+'.yaml',
                           'preConditions':[{'onFail':'HALT'},{'dbms':{'type':'postgresql'}},{'sqlCheck':{'expectedResult':'1','sql':guard}}],
                           'changes':[{'sqlFile':{'path':stem+'.sql','relativeToChangelogFile':True}}],
                           'rollback':[{'sqlFile':{'path':stem+'-rollback.sql','relativeToChangelogFile':True,'splitStatements':False}}]}}]}
                files[base/(stem+'.yaml')] = json.dumps(payload,indent=2)+'\n'
                includes.append({'include':{'file':'../'+stem+'.yaml','relativeToChangelogFile':True}})
                if kind.endswith('tables'):
                    for fullname, table in selected.items():
                        if (table['module']=='platform') == (kind=='platform-tables'):
                            table['creation_changeset']=change
                            table['creation_file']=str(sqlpath)
            chain = [] if phase==1 else [{'include':{'file':f'BP{phase-1}.yaml','relativeToChangelogFile':True}}]
            files[base/'releases'/f'BP{phase}.yaml'] = json.dumps({'databaseChangeLog':chain+includes},indent=2)+'\n'
    # New owned durability tables are separate increments; never rewrite applied DATA-001 DDL.
    deferred_includes = []
    for fullname in ADDITIVE_TABLES:
        table = tables[fullname]
        owner, name = fullname.split('.')
        base = Path(f'services/{owner}/src/main/resources/db/changelog')
        fields = [column(field) for field in table['fields']]
        fields.append(f'CONSTRAINT {name}_id_pk PRIMARY KEY (id)')
        fields.append(f'CONSTRAINT {name}_version_ck CHECK (version >= 0)')
        for field in table['fields']:
            if field['type'] == 'uuid':
                fields.append(f"CONSTRAINT {name}_{field['name']}_nonnil_ck CHECK ({field['name']} <> '00000000-0000-0000-0000-000000000000'::uuid)")
        for i, check in enumerate(table['checks'].split(';'), 1):
            fields.append(f'CONSTRAINT {name}_rule_{i}_ck CHECK ({predicate(check.strip())})')
        statements = [f'CREATE TABLE {fullname} (\n    ' + ',\n    '.join(fields) + '\n);']
        statements.append(f'GRANT SELECT, INSERT, UPDATE, DELETE ON {fullname} TO nigson_{owner}_app;')
        for key in table['keys'].split(';'):
            match = re.fullmatch(r'(UQ|IX)\((.*?)\)(?: WHERE (.*))?', key.strip())
            if not match:
                raise ValueError('Unrecognized additive key: '+fullname+': '+key)
            kind, columns, where = match.groups()
            suffix = re.sub(r'[^a-z0-9]+', '_', columns.lower()).strip('_') + ('_uq' if kind == 'UQ' else '_ix')
            statements.append(f"CREATE {'UNIQUE ' if kind == 'UQ' else ''}INDEX {identifier(name+'_'+suffix)} ON {fullname} ({columns})" + (' WHERE '+predicate(where) if where else '') + ';')
        for field in table['fields']:
            if field['foreign']:
                target = field['foreign'].split('.')[0]
                statements.append(f"ALTER TABLE {fullname} ADD CONSTRAINT {name}_{field['name']}_fk FOREIGN KEY ({field['name']}) REFERENCES {owner}.{target} (id) ON DELETE RESTRICT;")
        stem, change, author, header = ADDITIVE_TABLES[fullname]
        files[base/(stem+'.sql')] = '-- '+header+'\n' + '\n\n'.join(statements)+'\n'
        files[base/(stem+'-rollback.sql')] = rollback_sql(owner, statements)
        guard = f"SELECT CASE WHEN current_user='nigson_{owner}_migrator' AND current_setting('server_version_num')::int BETWEEN 180000 AND 189999 THEN 1 ELSE 0 END"
        payload = {'databaseChangeLog':[{'changeSet':{'id':change,'author':author,'logicalFilePath':owner+'/'+stem+'.yaml','preConditions':[{'onFail':'HALT'},{'dbms':{'type':'postgresql'}},{'sqlCheck':{'expectedResult':'1','sql':guard}}],'changes':[{'sqlFile':{'path':stem+'.sql','relativeToChangelogFile':True}}],'rollback':[{'sqlFile':{'path':stem+'-rollback.sql','relativeToChangelogFile':True,'splitStatements':False}}]}}]}
        files[base/(stem+'.yaml')] = json.dumps(payload,indent=2)+'\n'
        wrapper = base/'releases'/'BP1.yaml'
        include = {'include':{'file':'../'+stem+'.yaml','relativeToChangelogFile':True}}
        if fullname == 'commerce.file_transfer':
            payload = json.loads(files[wrapper]); payload['databaseChangeLog'].append(include)
            files[wrapper] = json.dumps(payload,indent=2)+'\n'
        else:
            deferred_includes.append((wrapper, include))
        table['creation_changeset']=change
        table['creation_file']=str(base/(stem+'.sql'))
    # Explicitly approved country/currency metadata is an additive data changeset, not domain DDL.
    from nigeria import files as nigeria_files, BASE as nigeria_base, STEM as nigeria_stem
    files.update(nigeria_files())
    wrapper=nigeria_base/'releases'/'BP1.yaml'
    payload=json.loads(files[wrapper]);payload['databaseChangeLog'].append({'include':{'file':'../'+nigeria_stem+'.yaml','relativeToChangelogFile':True}})
    files[wrapper]=json.dumps(payload,indent=2)+'\n'
    for wrapper, include in deferred_includes:
        payload=json.loads(files[wrapper]);payload['databaseChangeLog'].append(include)
        files[wrapper]=json.dumps(payload,indent=2)+'\n'
    # API-190 admits phone-only inbound leads without synthesizing email data.
    # The historical BP1 CREATE TABLE stays immutable; this is an additive change.
    stem='023-msg-001-lead-email-nullable';change='experience-20261016-023';owner='experience';base=Path('services/experience/src/main/resources/db/changelog')
    statements=['ALTER TABLE experience.lead ALTER COLUMN email DROP NOT NULL;']
    files[base/(stem+'.sql')]='-- ADR-019: Experience-owned phone-only verified inbound leads.\n'+'\n'.join(statements)+'\n'
    files[base/(stem+'-rollback.sql')]=("DO $$ BEGIN IF EXISTS (SELECT 1 FROM experience.lead WHERE email IS NULL) THEN RAISE EXCEPTION 'Cannot restore lead.email NOT NULL while phone-only inbound leads exist'; END IF; END $$;\nALTER TABLE experience.lead ALTER COLUMN email SET NOT NULL;\n")
    guard="SELECT CASE WHEN current_user='nigson_experience_migrator' AND current_setting('server_version_num')::int BETWEEN 180000 AND 189999 THEN 1 ELSE 0 END"
    payload={'databaseChangeLog':[{'changeSet':{'id':change,'author':'nigson-platform','logicalFilePath':owner+'/'+stem+'.yaml','preConditions':[{'onFail':'HALT'},{'dbms':{'type':'postgresql'}},{'sqlCheck':{'expectedResult':'1','sql':guard}}],'changes':[{'sqlFile':{'path':stem+'.sql','relativeToChangelogFile':True}}],'rollback':[{'sqlFile':{'path':stem+'-rollback.sql','relativeToChangelogFile':True,'splitStatements':False}}]}}]}
    files[base/(stem+'.yaml')]=json.dumps(payload,indent=2)+'\n'
    wrapper=base/'releases'/'BP1.yaml';payload=json.loads(files[wrapper]);payload['databaseChangeLog'].append({'include':{'file':'../'+stem+'.yaml','relativeToChangelogFile':True}});files[wrapper]=json.dumps(payload,indent=2)+'\n'
    stem='024-msg-001-inbound-retention';change='experience-20261016-024';statements=['ALTER TABLE experience.inbound_message ALTER COLUMN sender_ciphertext DROP NOT NULL;']
    files[base/(stem+'.sql')]='-- ADR-019: erase sender ciphertext at the explicit inbound retention boundary.\n'+'\n'.join(statements)+'\n'
    files[base/(stem+'-rollback.sql')]=("DO $$ BEGIN IF EXISTS (SELECT 1 FROM experience.inbound_message WHERE sender_ciphertext IS NULL) THEN RAISE EXCEPTION 'Cannot restore inbound sender ciphertext NOT NULL while retention-erased records exist'; END IF; END $$;\nALTER TABLE experience.inbound_message ALTER COLUMN sender_ciphertext SET NOT NULL;\n")
    payload={'databaseChangeLog':[{'changeSet':{'id':change,'author':'nigson-platform','logicalFilePath':'experience/'+stem+'.yaml','preConditions':[{'onFail':'HALT'},{'dbms':{'type':'postgresql'}},{'sqlCheck':{'expectedResult':'1','sql':guard}}],'changes':[{'sqlFile':{'path':stem+'.sql','relativeToChangelogFile':True}}],'rollback':[{'sqlFile':{'path':stem+'-rollback.sql','relativeToChangelogFile':True,'splitStatements':False}}]}}]}
    files[base/(stem+'.yaml')]=json.dumps(payload,indent=2)+'\n';payload=json.loads(files[wrapper]);payload['databaseChangeLog'].append({'include':{'file':'../'+stem+'.yaml','relativeToChangelogFile':True}});files[wrapper]=json.dumps(payload,indent=2)+'\n'
    for name in SESSION_TABLES:
        tables[name]['creation_changeset'] = 'commerce-20261005-002'
        tables[name]['creation_file'] = 'services/commerce/src/main/resources/db/changelog/001-framework-sessions.sql'
    files[Path('database/schema-manifest.json')] = json.dumps(manifest,indent=2,sort_keys=True)+'\n'
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--check',action='store_true')
    args = parser.parse_args()
    for path, content in generate().items():
        target = ROOT/path
        if args.check:
            if not target.is_file() or target.read_text()!=content:
                raise SystemExit('Catalog artifact drift: '+str(path))
        else:
            target.parent.mkdir(parents=True,exist_ok=True)
            target.write_text(content)
    print('PASS: generated catalog matches reviewed migration artifacts' if args.check else 'Generated section-10 migration artifacts; database verification still required')


if __name__ == '__main__':
    main()
