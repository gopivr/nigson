"""Initialize only the dedicated identity database using supplied owner credentials."""
import os
import re

import psycopg
from psycopg import sql


def bootstrap(admin_dsn: str, password: str, environment: str):
    if environment not in {"test", "dev", "qa", "stage", "prod"} or not password or environment != "test" and password.startswith(("mock-", "test-")):
        raise ValueError("IDENTITY_DATABASE_INPUT_REQUIRED")
    info = psycopg.conninfo.conninfo_to_dict(admin_dsn)
    if info.get("sslmode") != "verify-full" or not info.get("sslrootcert") or not info.get("host"):
        raise ValueError("VERIFIED_IDENTITY_DATABASE_TLS_REQUIRED")
    if info.get("dbname", "postgres") != "postgres":
        raise ValueError("DEDICATED_IDENTITY_BOOTSTRAP_REQUIRED")
    with psycopg.connect(admin_dsn, autocommit=True, connect_timeout=5) as conn:
        # Never rotate an existing role implicitly. Credential rotation is an explicit paired rollout.
        if not conn.execute("SELECT EXISTS(SELECT 1 FROM pg_roles WHERE rolname='nigson_identity')").fetchone()[0]:
            conn.execute(sql.SQL("CREATE ROLE nigson_identity LOGIN PASSWORD {} NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS").format(sql.Literal(password)))
        flags = conn.execute("SELECT rolsuper,rolcreatedb,rolcreaterole,rolreplication,rolbypassrls FROM pg_roles WHERE rolname='nigson_identity'").fetchone()
        if any(flags):
            raise ValueError("IDENTITY_ROLE_PRIVILEGE_DRIFT")
        if not conn.execute("SELECT EXISTS(SELECT 1 FROM pg_database WHERE datname='keycloak')").fetchone()[0]:
            conn.execute("CREATE DATABASE keycloak OWNER nigson_identity ENCODING 'UTF8'")
        owner = conn.execute("SELECT pg_get_userbyid(datdba) FROM pg_database WHERE datname='keycloak'").fetchone()[0]
        if owner != "nigson_identity":
            raise ValueError("IDENTITY_DATABASE_OWNER_DRIFT")
        conn.execute("REVOKE CONNECT,TEMPORARY ON DATABASE keycloak FROM PUBLIC")
        conn.execute("GRANT CONNECT,TEMPORARY ON DATABASE keycloak TO nigson_identity")
    private = {**info, "dbname": "keycloak", "user": "nigson_identity", "password": password}
    with psycopg.connect(**private, connect_timeout=5) as conn:
        conn.execute("REVOKE CREATE ON SCHEMA public FROM PUBLIC")
        conn.execute("ALTER SCHEMA public OWNER TO nigson_identity")


if __name__ == "__main__":
    try:
        bootstrap(os.environ["IDENTITY_ADMIN_DSN"], os.environ["IDENTITY_OWNER_PASSWORD"], os.environ["APP_ENV"])
        print("IDENTITY_DATABASE_BOOTSTRAP_COMPLETE")
    except Exception:
        raise SystemExit("IDENTITY_DATABASE_BOOTSTRAP_REJECTED") from None
