-- Local-only, idempotent provisioning. Applied explicitly on existing volumes.
BEGIN;
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
DO $bootstrap$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'commerce_migration') AND
     EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_commerce_migrator') THEN
    RAISE EXCEPTION 'Ambiguous local owner roles: commerce/migrator';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'commerce_migration') AND
     NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_commerce_migrator') THEN
    ALTER ROLE commerce_migration RENAME TO nigson_commerce_migrator;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_commerce_migrator') THEN
    CREATE ROLE nigson_commerce_migrator LOGIN PASSWORD 'mock-commerce-migration';
  END IF;
END
$bootstrap$;
ALTER ROLE nigson_commerce_migrator NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE nigson_commerce_migrator SET search_path = commerce;
DO $bootstrap$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'commerce_runtime') AND
     EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_commerce_app') THEN
    RAISE EXCEPTION 'Ambiguous local owner roles: commerce/app';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'commerce_runtime') AND
     NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_commerce_app') THEN
    ALTER ROLE commerce_runtime RENAME TO nigson_commerce_app;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_commerce_app') THEN
    CREATE ROLE nigson_commerce_app LOGIN PASSWORD 'mock-commerce-runtime';
  END IF;
END
$bootstrap$;
ALTER ROLE nigson_commerce_app NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE nigson_commerce_app SET search_path = commerce;
DO $bootstrap$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'commerce'
             AND pg_get_userbyid(nspowner) <> 'nigson_commerce_migrator') THEN
    RAISE EXCEPTION 'Unexpected local schema owner: commerce';
  END IF;
END
$bootstrap$;
CREATE SCHEMA IF NOT EXISTS commerce AUTHORIZATION nigson_commerce_migrator;
ALTER SCHEMA commerce OWNER TO nigson_commerce_migrator;
REVOKE ALL ON SCHEMA commerce FROM PUBLIC;
GRANT USAGE ON SCHEMA commerce TO nigson_commerce_app;
DO $bootstrap$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'experience_migration') AND
     EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_experience_migrator') THEN
    RAISE EXCEPTION 'Ambiguous local owner roles: experience/migrator';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'experience_migration') AND
     NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_experience_migrator') THEN
    ALTER ROLE experience_migration RENAME TO nigson_experience_migrator;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_experience_migrator') THEN
    CREATE ROLE nigson_experience_migrator LOGIN PASSWORD 'mock-experience-migration';
  END IF;
END
$bootstrap$;
ALTER ROLE nigson_experience_migrator NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE nigson_experience_migrator SET search_path = experience;
DO $bootstrap$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'experience_runtime') AND
     EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_experience_app') THEN
    RAISE EXCEPTION 'Ambiguous local owner roles: experience/app';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'experience_runtime') AND
     NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_experience_app') THEN
    ALTER ROLE experience_runtime RENAME TO nigson_experience_app;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_experience_app') THEN
    CREATE ROLE nigson_experience_app LOGIN PASSWORD 'mock-experience-runtime';
  END IF;
END
$bootstrap$;
ALTER ROLE nigson_experience_app NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE nigson_experience_app SET search_path = experience;
DO $bootstrap$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'experience'
             AND pg_get_userbyid(nspowner) <> 'nigson_experience_migrator') THEN
    RAISE EXCEPTION 'Unexpected local schema owner: experience';
  END IF;
END
$bootstrap$;
CREATE SCHEMA IF NOT EXISTS experience AUTHORIZATION nigson_experience_migrator;
ALTER SCHEMA experience OWNER TO nigson_experience_migrator;
REVOKE ALL ON SCHEMA experience FROM PUBLIC;
GRANT USAGE ON SCHEMA experience TO nigson_experience_app;
DO $bootstrap$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'integration_migration') AND
     EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_integration_migrator') THEN
    RAISE EXCEPTION 'Ambiguous local owner roles: integration/migrator';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'integration_migration') AND
     NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_integration_migrator') THEN
    ALTER ROLE integration_migration RENAME TO nigson_integration_migrator;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_integration_migrator') THEN
    CREATE ROLE nigson_integration_migrator LOGIN PASSWORD 'mock-integration-migration';
  END IF;
END
$bootstrap$;
ALTER ROLE nigson_integration_migrator NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE nigson_integration_migrator SET search_path = integration;
DO $bootstrap$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'integration_runtime') AND
     EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_integration_app') THEN
    RAISE EXCEPTION 'Ambiguous local owner roles: integration/app';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'integration_runtime') AND
     NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_integration_app') THEN
    ALTER ROLE integration_runtime RENAME TO nigson_integration_app;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_integration_app') THEN
    CREATE ROLE nigson_integration_app LOGIN PASSWORD 'mock-integration-runtime';
  END IF;
END
$bootstrap$;
ALTER ROLE nigson_integration_app NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE nigson_integration_app SET search_path = integration;
DO $bootstrap$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'integration'
             AND pg_get_userbyid(nspowner) <> 'nigson_integration_migrator') THEN
    RAISE EXCEPTION 'Unexpected local schema owner: integration';
  END IF;
END
$bootstrap$;
CREATE SCHEMA IF NOT EXISTS integration AUTHORIZATION nigson_integration_migrator;
ALTER SCHEMA integration OWNER TO nigson_integration_migrator;
REVOKE ALL ON SCHEMA integration FROM PUBLIC;
GRANT USAGE ON SCHEMA integration TO nigson_integration_app;
DO $bootstrap$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_migration') AND
     EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_ai_migrator') THEN
    RAISE EXCEPTION 'Ambiguous local owner roles: ai/migrator';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_migration') AND
     NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_ai_migrator') THEN
    ALTER ROLE ai_migration RENAME TO nigson_ai_migrator;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_ai_migrator') THEN
    CREATE ROLE nigson_ai_migrator LOGIN PASSWORD 'mock-ai-migration';
  END IF;
END
$bootstrap$;
ALTER ROLE nigson_ai_migrator NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE nigson_ai_migrator SET search_path = ai;
DO $bootstrap$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_runtime') AND
     EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_ai_app') THEN
    RAISE EXCEPTION 'Ambiguous local owner roles: ai/app';
  END IF;
  IF EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'ai_runtime') AND
     NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_ai_app') THEN
    ALTER ROLE ai_runtime RENAME TO nigson_ai_app;
  END IF;
  IF NOT EXISTS (SELECT 1 FROM pg_roles WHERE rolname = 'nigson_ai_app') THEN
    CREATE ROLE nigson_ai_app LOGIN PASSWORD 'mock-ai-runtime';
  END IF;
END
$bootstrap$;
ALTER ROLE nigson_ai_app NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION NOBYPASSRLS;
ALTER ROLE nigson_ai_app SET search_path = ai;
DO $bootstrap$
BEGIN
  IF EXISTS (SELECT 1 FROM pg_namespace WHERE nspname = 'ai'
             AND pg_get_userbyid(nspowner) <> 'nigson_ai_migrator') THEN
    RAISE EXCEPTION 'Unexpected local schema owner: ai';
  END IF;
END
$bootstrap$;
CREATE SCHEMA IF NOT EXISTS ai AUTHORIZATION nigson_ai_migrator;
ALTER SCHEMA ai OWNER TO nigson_ai_migrator;
REVOKE ALL ON SCHEMA ai FROM PUBLIC;
GRANT USAGE ON SCHEMA ai TO nigson_ai_app;
COMMIT;
