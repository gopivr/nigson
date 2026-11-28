"""Private one-shot database/realm provisioning. ECS supplies all secret values."""
import base64
import json
import os
from pathlib import Path
import tempfile
import zlib
import psycopg
from psycopg import sql
from admin import Admin
from database import bootstrap as identity_database


def required(name):
    value=os.environ.get(name,'')
    if not value:raise ValueError('RUNTIME_SETUP_CONFIGURATION_REQUIRED')
    return value


def database_roles(host, user, password, ca, owners, credentials):
    if not host.endswith('.rds.amazonaws.com') or set(owners)-{'commerce','experience','integration','ai'}:
        raise ValueError('RUNTIME_DATABASE_TARGET_REJECTED')
    connection_args=dict(host=host,port=5432,dbname='nigson',sslmode='verify-full',sslrootcert=ca,connect_timeout=10)
    with psycopg.connect(**connection_args,user=user,password=password) as connection:
        connection.execute("SELECT pg_advisory_xact_lock(hashtext('nigson-initial-owner-bootstrap'))")
        if not 180000<=int(connection.execute('SHOW server_version_num').fetchone()[0])<190000:raise ValueError('POSTGRESQL_18_REQUIRED')
        connection.execute('REVOKE CREATE ON SCHEMA public FROM PUBLIC')
        for owner in owners:
            for suffix in ['migrator','app','audit']:
                role='nigson_'+owner+'_'+suffix
                existing=connection.execute('SELECT rolsuper,rolcreatedb,rolcreaterole,rolreplication,rolbypassrls FROM pg_roles WHERE rolname=%s',(role,)).fetchone()
                if existing and any(existing):raise ValueError('OWNER_ROLE_PRIVILEGE_DRIFT')
                if not existing:
                    if suffix=='audit':connection.execute(sql.SQL('CREATE ROLE {} NOLOGIN').format(sql.Identifier(role)))
                    else:connection.execute(sql.SQL('CREATE ROLE {} LOGIN PASSWORD {} NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS').format(sql.Identifier(role),sql.Literal(credentials[role])))
                connection.execute(sql.SQL('ALTER ROLE {} SET search_path={}').format(sql.Identifier(role),sql.Identifier(owner)))
            current=connection.execute('SELECT pg_get_userbyid(nspowner) FROM pg_namespace WHERE nspname=%s',(owner,)).fetchone()
            if current and current[0]!='nigson_'+owner+'_migrator':raise ValueError('OWNER_SCHEMA_DRIFT')
            connection.execute(sql.SQL('CREATE SCHEMA IF NOT EXISTS {} AUTHORIZATION {}').format(sql.Identifier(owner),sql.Identifier('nigson_'+owner+'_migrator')))
            connection.execute(sql.SQL('REVOKE ALL ON SCHEMA {} FROM PUBLIC').format(sql.Identifier(owner)))
            for suffix in ['app','audit']:
                role='nigson_'+owner+'_'+suffix
                connection.execute(sql.SQL('REVOKE ALL ON SCHEMA {} FROM {}').format(sql.Identifier(owner),sql.Identifier(role)))
                connection.execute(sql.SQL('GRANT USAGE ON SCHEMA {} TO {}').format(sql.Identifier(owner),sql.Identifier(role)))
        for owner in owners:
            for other in owners:
                if owner!=other:
                    for suffix in ['migrator','app','audit']:
                        connection.execute(sql.SQL('REVOKE ALL ON SCHEMA {} FROM {}').format(sql.Identifier(owner),sql.Identifier('nigson_'+other+'_'+suffix)))
        if 'ai' in owners:
            connection.execute('CREATE EXTENSION IF NOT EXISTS vector WITH SCHEMA ai')
            if connection.execute("SELECT extversion FROM pg_extension WHERE extname='vector' AND extnamespace='ai'::regnamespace").fetchone()!=('0.8.2',):raise ValueError('APPROVED_PGVECTOR_VERSION_REQUIRED')
    # Existing passwords are verified, never implicitly rotated.
    for role,value in credentials.items():
        with psycopg.connect(**connection_args,user=role,password=value) as connection:
            if connection.execute('SELECT current_user').fetchone()[0]!=role:raise ValueError('OWNER_LOGIN_REJECTED')


def main():
    if required('APP_ENV')!='stage':raise ValueError('FIRST_ACCOUNT_STAGING_ONLY')
    mode=required('SETUP_MODE')
    if mode=='database':
        with tempfile.TemporaryDirectory() as folder:
            ca=Path(folder)/'rds.pem';ca.write_text(required('SETUP_DB_CA_PEM'));ca.chmod(0o600)
            owners=json.loads(required('SETUP_OWNERS'))
            credentials={'nigson_'+owner+'_'+kind:required('SETUP_'+owner.upper()+'_'+kind.upper()+'_PASSWORD') for owner in owners for kind in ['migrator','app']}
            database_roles(required('SETUP_DATABASE_HOST'),required('SETUP_DATABASE_ADMIN_USER'),required('SETUP_DATABASE_ADMIN_PASSWORD'),str(ca),owners,credentials)
            dsn=psycopg.conninfo.make_conninfo(host=required('SETUP_IDENTITY_DATABASE_HOST'),port=5432,dbname='postgres',user=required('SETUP_IDENTITY_ADMIN_USER'),password=required('SETUP_IDENTITY_ADMIN_PASSWORD'),sslmode='verify-full',sslrootcert=str(ca))
            identity_database(dsn,required('SETUP_IDENTITY_OWNER_PASSWORD'),'stage')
    elif mode=='realm':
        policy=json.loads(zlib.decompress(base64.b64decode(required('SETUP_REALM_POLICY'))))
        if policy.get('realm')!='nigson-stage':raise ValueError('UNEXPECTED_REALM')
        secrets={client['clientId']:required('SETUP_CLIENT_'+client['clientId'].removeprefix('nigson-').upper()) for client in policy['clients']}
        admin=Admin(required('SETUP_ADMIN_ORIGIN'),None)
        admin.login(required('SETUP_BOOTSTRAP_USER'),required('SETUP_BOOTSTRAP_PASSWORD'))
        admin.provision(policy,secrets)
    else:raise ValueError('UNEXPECTED_SETUP_MODE')
    print('RUNTIME_'+mode.upper()+'_PROVISIONED')


if __name__=='__main__':
    try:main()
    except Exception:raise SystemExit('RUNTIME_PROVISIONING_FAILED: verify task configuration and database/identity readiness; no credentials were logged') from None
