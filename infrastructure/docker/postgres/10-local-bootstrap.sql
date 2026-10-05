-- Infrastructure bootstrap only. Business schemas/migrations are DATA-001.
-- This file is mounted only by the local Compose definition.
REVOKE CREATE ON SCHEMA public FROM PUBLIC;
CREATE ROLE commerce_migration LOGIN PASSWORD 'mock-commerce-migration';
CREATE ROLE commerce_runtime LOGIN PASSWORD 'mock-commerce-runtime';
CREATE ROLE experience_migration LOGIN PASSWORD 'mock-experience-migration';
CREATE ROLE experience_runtime LOGIN PASSWORD 'mock-experience-runtime';
CREATE ROLE integration_migration LOGIN PASSWORD 'mock-integration-migration';
CREATE ROLE integration_runtime LOGIN PASSWORD 'mock-integration-runtime';
CREATE ROLE ai_migration LOGIN PASSWORD 'mock-ai-migration';
CREATE ROLE ai_runtime LOGIN PASSWORD 'mock-ai-runtime';
CREATE SCHEMA commerce AUTHORIZATION commerce_migration;
CREATE SCHEMA experience AUTHORIZATION experience_migration;
CREATE SCHEMA integration AUTHORIZATION integration_migration;
CREATE SCHEMA ai AUTHORIZATION ai_migration;
REVOKE ALL ON SCHEMA commerce, experience, integration, ai FROM PUBLIC;
GRANT USAGE ON SCHEMA commerce TO commerce_runtime;
GRANT USAGE ON SCHEMA experience TO experience_runtime;
GRANT USAGE ON SCHEMA integration TO integration_runtime;
GRANT USAGE ON SCHEMA ai TO ai_runtime;
ALTER ROLE commerce_migration SET search_path = commerce;
ALTER ROLE commerce_runtime SET search_path = commerce;
ALTER ROLE experience_migration SET search_path = experience;
ALTER ROLE experience_runtime SET search_path = experience;
ALTER ROLE integration_migration SET search_path = integration;
ALTER ROLE integration_runtime SET search_path = integration;
ALTER ROLE ai_migration SET search_path = ai;
ALTER ROLE ai_runtime SET search_path = ai;
