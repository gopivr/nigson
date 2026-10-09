# Nigson Local Development

Specification1.1.0; synchronized view; master is authoritative.

## Implemented command status (5 October 2026)

FOUND-001 implements `make status-check`. FOUND-002 adds `make build`, `make test`,
`make verify`, `make dependency-check` and their optional `PROFILE=ai` build/test
composition. Export the pinned Oracle JDK as JAVA_HOME; Maven rejects the default
Java 17. AI requires uv 0.9.3 (override `UV` to its installed path), installs
Python 3.12.12, and uses only the committed uv.lock. Caches are workspace-local.
See DEPENDENCY_LOCK.md and task reports for executed evidence and limitations.
FOUND-003 implements the foundation workflow below. These services expose real
process/database readiness and deny all domain routes; domain APIs, provider
adapters, business fixtures and full OIDC/BFF authorization remain their roadmap
tasks. The optional AI runtime exposes database/vector health only; it does not
activate AI features or call a model.

```bash
cp .env.example .env
make local-config-check
make local-up
make migrate ENV=local
make seed ENV=local FIXTURE_PROFILE=foundation
make run-core
make local-status
make verify
# Optional BP5 foundation qualification only:
make local-up PROFILE=ai
make migrate ENV=local PROFILE=ai
make run-ai
# Independent fresh test namespace; retains its own volumes and stops its containers:
make cold-start
make local-down
```

`PROFILE=core` starts only the five vendor dependencies. `PROFILE=app` selects
Java application definitions; `run-core` builds and starts them after separate
owner migration jobs. `PROFILE=ai` adds a private vector database and the optional
Python health runtime. `local-up` starts dependencies only, waits for real
Keycloak discovery, and bootstraps LocalStack. `--no-recreate` preserves existing
container identities; configuration changes requiring recreation must be applied
explicitly after assessing their persisted state. `local-down` retains all named
volumes. No reset or prune is performed by setup, seed or acceptance tests.

Java health paths are `/actuator/health/liveness` and
`/actuator/health/readiness` on localhost:8081/8082/8083. Python uses
`/health/live` and `/health/ready` on localhost:8084. Host ports are configurable
through COMMERCE_PORT, EXPERIENCE_PORT, INTEGRATION_PORT, AI_PORT, NIGSON_DB_PORT,
KEYCLOAK_PORT, LOCALSTACK_PORT and MAILPIT_HTTP_PORT/MAILPIT_SMTP_PORT. All binds
remain loopback. Migration URLs use private Compose hostnames and are guarded.
Scripts refuse remote Docker endpoints, nonlocal environments/projects, unknown
profiles, production application configuration, remote JDBC targets and missing
local credentials. Python/uv is required on the host only for optional AI build
checks; Docker builds the optional image with locked dependencies.

Migration provisioning upgrades legacy role names to `nigson_<owner>_migrator`
and `nigson_<owner>_app` in place, retaining role OIDs/ownership/data. Ambiguous
roles or unexpected schema owners halt provisioning atomically. Local roles use
the visibly mock credentials declared in `.env.example`; existing credentials
are never silently reset. Each owner has its own master and Liquibase tracking
schema. The one-shot tool validates checksums, reports pending changes, generates
SQL before applying, then checks bootstrap privileges and session constraints.
Applications never run Liquibase or Hibernate/session DDL. Do not clear checksums,
drop schemas or delete volumes to conceal drift.

`FIXTURE_PROFILE=foundation` explicitly selects the 13 deterministic synthetic
Keycloak subjects in `tests/fixtures/local-identities.json`. Seed fills missing
synthetic profile names, verifies stable IDs/emails/roles, and obtains a genuine
signed token for every subject using KEYCLOAK_FIXTURE_PASSWORD. Password drift
fails; existing passwords are not overwritten. The test-only
`nigson-local-fixtures` client enables direct grants for local integration tests;
the BFF client retains authorization-code/PKCE settings. No token bypass exists.
Seed creates no domain tables, catalog prices, tax policies or provider accounts.
Foundation Spring Session tables are real locked-version framework tables;
DATA-001 must retain them when adding business tables, rather than recreate them.

`make command-surface-check` runs repository/local command contracts, also included
in `make verify`. `make cold-start` executes the full workflow twice in a unique
`nigson-test-cold-*` namespace with fresh volumes and reserved loopback ports,
checks owner denial/session persistence/checksum and constraint drift/real signed
JWTs/invalid signatures/vector queries/missing credentials/production refusal,
then tests DB outage readiness and recovery. It compares existing nigson-local
container IDs, start times, mounts and volume names before/after. Its JSON evidence
is `.build/evidence/found003-cold-start.json`; test volumes remain inspectable.

Sequencing boundary: sections36/40 describe the eventual catalog/provider fixtures,
contract regeneration, load and restore workflows. DATA-001 depends on FOUND-003
and owns business schemas; MOCK-001 owns provider commands; PLAT/INFRA/REL tasks
own business load/restore/release qualification. Generating those business seeds
or claiming these future checks at FOUND-003 would contradict the dependency
roadmap and its “Owned existing tables / APIs None directly” contract. The
foundation profile and command checks above qualify this task; the later task
commands below remain specified outputs, not successful placeholder targets.


# Master section30: Configuration Management

Profiles local, dev, test, qa, stage, prod; qa may reuse staging infrastructure shape with its own state/secrets. Use typed @ConfigurationProperties/Pydantic Settings with startup validation. Local/test may supply deterministic adapters; stage/prod reject fake payment/model adapters, permissive CORS, HTTP issuer and embedded credentials.

Required groups: service identity/public/internal base URLs; JDBC owner URL/user/secret ref; OIDC issuer/client/resource scopes; BFF allowed return origins and signing KMS ref; SNS/SQS ARNs/URLs; S3 bucket/purpose policies; payment provider/secret/merchant/approved currencies; email provider/sender/recipient groups; Sheet ID/credential ref/field allowlist; required FedEx carrier; feature flags; market/tax/reward/retention policies; AI region/model/prompt/rate/budget revisions; observability exporters and sampling. No environment-specific URL in Java/Python source. `.env.example` contains examples/empty secret references only, no production endpoint assumption.

Runtime flags default false for rewards/wheel/international/AI/carrier/MCP. Activation validates complete policy plus approval evidence and writes audit. A feature disabled still rejects unauthorized access and reports FEATURE_DISABLED consistently; flags cannot bypass role checks. Missing enabled-provider config causes startup/activation failure, not silent fallback to a mock. Session/financial deadlines must be within documented limits. Use Secrets Manager runtime references and ECS task IAM; no secrets in Terraform state values, image args, Git history, completion reports or .env.example.


## Launch provider configuration and credential replacement

| Group | Required settings |
|---|---|
| Market | LAUNCH_MARKET=NG, LAUNCH_COUNTRY=NG, LAUNCH_CURRENCY=NGN, minor_unit2; tax/returns/book/origin/service-area policies separately versioned |
| Paystack | PAYMENT_PROVIDER=paystack only; PAYSTACK_MODE=mock\|test\|live, PAYSTACK_BASE_URL, PAYSTACK_SECRET_KEY_REF, merchant/account reference, public callback/webhook URLs |
| FedEx | CARRIER_PROVIDER=fedex; FEDEX_MODE=mock\|sandbox\|live, FEDEX_BASE_URL, CLIENT_ID/SECRET refs, account number ref, shipper origin, enabled service/package policies, poll interval and label bucket |
| Twilio | SMS_PROVIDER=twilio, WHATSAPP_PROVIDER=twilio; independent channel enabled flags; TWILIO_MODE=mock\|sandbox\|live, BASE_URL, ACCOUNT_SID/AUTH_TOKEN refs, SMS sender/MessagingServiceSid, WhatsApp sender/ContentSid map, exact status/inbound public URLs |
| Keycloak | KEYCLOAK_IMAGE_DIGEST, dedicated DB/secret refs, realm issuer, public hostname/private admin hostname, trusted proxy settings, BFF/workload client secret refs, MFA/realm/client/audience policy |

Local/test defaults point to private Compose mock hostnames and visibly synthetic credentials such as mock-paystack-secret, mock-fedex-client and mock-twilio-token. .env.example may include these non-secret mock values and empty real secret references. No fake key is sent to a genuine provider endpoint. Mode selects target/auth/config, never skips business invariants or callback signature verification. Mock callback secrets/signatures are real algorithms over dummy secrets.

Production profile rejects mock/sandbox targets or dummy credentials for enabled financial/carrier/messaging features; real test/trial credentials run against provider staging or explicitly non-production release verification. Missing enabled-provider config fails validation rather than silently falling back. Staging can run mock or genuine sandbox with recipient allowlist and separate data. A live environment with features disabled may start for infrastructure checks but cannot claim completed live checkout/fulfillment/messages. Keycloak production configuration is required for production identity regardless of provider test mode.


# Master section31: Docker

Generate a multi-stage Dockerfile for each Java/Python deployable during implementation. Java build uses Maven wrapper/Java25, runtime JRE25 minimal supported image pinned by digest, non-root UID10001, read-only root filesystem where feasible, temp volume, graceful SIGTERM and JVM container memory awareness. Python build creates a locked isolated environment, copies only package/runtime dependencies, non-root UID10001, uvicorn bounded workers and async pools. No embedded credential or package-download at runtime. Verify ARM compatibility before switching task architecture.

Health: GET /actuator/health/liveness and /actuator/health/readiness Java; GET /health/live and /health/ready Python. Liveness proves process/event-loop responsiveness, not third-party payment/AI availability. Readiness checks essential owned DB/schema initialization and safe configuration; external email/model outages do not restart otherwise healthy core services. Worker health includes its ability to poll/commit work; probe binds private interface and no public route. Container healthcheck uses the actual port and shell-free minimal command available in image; ECS/ALB health checks duplicate only necessary signals.

Local docker-compose.yml baseline: PostgreSQL18 pgvector-capable image (extension used only AI profile), LocalStack SNS/SQS/S3/SES emulation, Keycloak local realm, Mailpit, optional OTel collector. Full-app profile starts commerce8081, experience8082, worker8083 private, AI8084 only ai profile; dependencies ports localhost only. Valkey is optional cache profile, not default. No Kafka/OpenSearch/Kubernetes/Helm services. DB healthcheck plus one-shot migrations precede application readiness. Persistent named volumes are never deleted by default stop. LocalStack IAM emulation does not replace live staging IAM/security tests.


## Mock-first Compose and production identity

Add provider-mocks profile with separate stateful paystack-mock, fedex-mock and twilio-mock services using pinned WireMock images plus versioned test-only scenario extension/controllers. Compose core local setup includes these mocks and real local Keycloak. Testcontainers starts equivalent instances for CI; mock administration/reset/journal is localhost/internal-test-network only. Callback dispatcher sends signed HTTP to the real app facades; failure/scenario controls are never public production APIs. Tests prohibit external paid network access. Production builds exclude mock controllers, images, dummy credentials and test admin routes.

Keycloak image is pinned/optimized in production, supported external PostgreSQL, private management/health/admin, TLS/proxy/hostname and cluster settings; local start-dev is local only. Do not reuse Keycloak development filesystem DB or default admin credentials for release.


# Master section36: Test Data

Fixtures live at service src/test/resources/fixtures and shared tests/fixtures; deterministic UUIDs, fixed Clock `2026-10-04T00:00:00Z`, seeded randomness only in tests, synthetic `example.invalid` emails and no real staff/customer data. Fixture identities: retailA, retailB, orgA owner/buyer, orgB buyer, editor, publisher, sales, financeA/financeB, operations, analyst and admin. Local Keycloak users are examples with credentials from local-only env, never production seeds.

Catalogue: three categories, two brands, five products/ten SKUs, one initial location, some out-of-stock/ineligible/archived variants. Default synthetic NG market countryNG/NGN minor-unit2 reflects confirmed launch metadata; addresses/users/tax/rates remain local/test-only; tests add a currency with3 decimals, fractional SKU, international-ineligible product, two price tiers and ambiguous delivery configurations that must fail. Tax tests use a clearly synthetic configured10 percent rate, not a legal tax recommendation. Promotion/reward fixtures use finite test budgets and verified policy revision; prod features remain disabled.

Provider fixtures cover Paystack charge.success, valid/invalid HMAC, duplicate object IDs, currency/amount mismatch, unknown reference, late success and provider HTTP timeout; refund fixtures cover processed/failed/pending and lost-response uncertainty. Sheet fixture has stable application ID column, CV filename inert text and no real resume. AI set has at least200 versioned cases divided facts/discovery/unsupported/private-scope/injection/withdrawal/provider failure. Include approved answer/source IDs and scoring rubric; test mock output cannot establish live model correctness.

Never seed arbitrary production ADMIN or a working merchant credential. Provision first administrator through a controlled subject assignment/runbook with audit and approved identity. Fixtures are segregated by Liquibase local/test context and explicit APP_ENV checks.


## Approved launch fixtures and mock scenarios

Default market NG/Nigeria/NGN2; synthetic Nigerian address/package metadata, fake users/numbers/provider accounts only. NGN examples1.00,1234.56 and overprecision1.001; wrong-currency USD is a negative test. No actual tax rate is inferred from country approval; synthetic tax/rates are labeled tests. Provision Paystack pending/success/failed/mismatch/duplicate/late/unknown/refund scenarios; FedEx OAuth-expiry/rate-unavailable/currency-mismatch/booked/response-lost/cancel/scan-progression; Twilio queued/sent/delivered/failed/undelivered/signed-invalid/out-of-order/STOP/proof-expiry. Use real local Keycloak, issuer/client negative cases and separate staff/organization actors. All recipient examples are mock-only; real sandbox allowlist is explicit and never automatically populated from examples.


# Master section39: Local Development Setup

Implementation must generate README.md, .env.example, docker-compose.yml, Makefile and idempotent setup scripts. Supported prerequisite: Docker Compose, Java25, Maven wrapper, Python3.12/uv only for ai profile; shell commands work from repository root.

```bash
cp .env.example .env
make local-up
make migrate ENV=local
make seed ENV=local
make run-core
make verify
# only when reaching EP12/BP5
make local-up PROFILE=ai
make run-ai
```

`local-up` starts required dependencies and waits on bounded health checks; it creates local SNS subscriptions/S3 buckets/Keycloak realm through deterministic scripts. `migrate` executes each owner role/master, fails on any schema drift and does not use Hibernate DDL. `seed` rejects any nonlocal/test env and requires explicit approved fixture profile. `run-core` starts Java services in separate processes/Compose app profile; reports URLs/health checks and clean stop handling. User login uses local OIDC flow; direct API tests obtain real local signed access tokens. Do not add a universal insecure bypass header.

Local services binds: BFF/core http://localhost:8081, experience8082, worker8083 localhost/private only, AI8084 optional, PostgreSQL5432 localhost, LocalStack4566, Keycloak8180, Mailpit8025. Origin URLs configurable and not compile-time constants. Use named volumes and separate test DB. .env is gitignored. Local dummy keys are clearly local-only and forbidden by prod validation; local HTTP cookie/issuer exception is profile-limited. Health/smoke checks confirm real persistence and identity flow before showing ready.


## Credential-free integration setup

```bash
make local-up PROFILES=provider-mocks
make migrate ENV=local
make seed ENV=local MARKET=NG CURRENCY=NGN
make mock-reset
make provider-contract-test PROVIDER=all MODE=mock
make provider-scenario PROVIDER=paystack SCENARIO=success
make provider-scenario PROVIDER=fedex SCENARIO=booking-response-lost
make provider-scenario PROVIDER=twilio SCENARIO=duplicate-status
make keycloak-config-check ENV=local
```

Implementation must create these commands in MOCK-001/IDP-001; they are specified commands, not scripts generated in this document-only task. make provider-live-check ENV=stage requires actual user-supplied account/credentials/sender/service config and may perform real provider sandbox operations; never run it automatically while keys are dummy.


# Master section40: Developer Experience

| Command | Behavior |
|---|---|
| make build | Maven package skip-tests only when explicitly asked; normal verify includes tests; AI locked build when enabled |
| make test / verify | Required unit/integration/security/contracts with real DB/emulator |
| make format / lint | Spotless plus Ruff/mypy and contract/schema lint |
| make local-up / local-down | Bounded start / graceful stop; never deletes volumes |
| make run-core / run-ai | Environment-validated processes, AI only at enabled phase |
| make migrate ENV=local | Owner Liquibase masters; each role used separately |
| make seed ENV=local | Deterministic local/test fixtures only |
| make reset-db ENV=local CONFIRM=reset-local | Explicit destructive local operation only, refuses remote/prod URLs and preserves backup option |
| make contracts | Regenerate OpenAPI and schemas; diff checked and reviewed |
| make load-test / restore-test | Realistic staging-safe synthetic workloads and isolated restore |
| make status-check | Verify task/report/status consistency; never infer test success |

Scripts use strict shell error handling, quote paths/variables, bounded readiness waits and clear exit codes. Do not repurpose HOME/system variables. Execution scripts must be safe to rerun and must not mask failures. Document exact generated command forms in LOCAL_DEVELOPMENT.md and task completion reports. Avoid requiring manual table edits, guessed ports or privileged cloud credentials for local development.


| Added command | Required behavior |
|---|---|
| make mock-reset | Reset local/test provider state only; refuse real targets. |
| make provider-contract-test PROVIDER=all MODE=mock | Adapters through HTTP mocks, signatures and schema/scenario assertions. |
| make provider-scenario PROVIDER=<name> SCENARIO=<fixture> | Bounded local fixture transition/callback; no arbitrary external URL. |
| make keycloak-config-check ENV=<env> | Realm/client/audience/MFA/hostname/admin/prod profile validation. |
| make provider-live-check ENV=stage | Explicit genuine sandbox credentials/account/sender/route smoke; record results honestly. |

### Schema release gates

`make migrate ENV=local SCHEMA_RELEASE=BP1` installs the launch schema. BP2–BP5
are cumulative explicit deployment gates; use `PROFILE=ai` to migrate the optional
local vector database. AI business/platform tables start only at BP5. A lower gate
than already applied is refused; rerunning the same gate validates checksums and
has no pending changes. Do not edit applied SQL or clear checksums.

`make schema-check` verifies checked-in catalog artifacts. `make database-check`
renders/validates the Terraform provisioning SQL, builds the migration image,
and qualifies all four schemas on one isolated PostgreSQL 18 / pgvector database.
It uses no published ports or provider calls and preserves `nigson-local` state.
The production database provisioning module is described in
`infrastructure/terraform/database/README.md`; RDS/ECS deployment remains INFRA-001.

With the pinned `JAVA_HOME`, run `make database-rollback-check` after the build
and database checks. It compiles a test-only Liquibase probe using the locked local
JARs, verifies empty additive rollback and BP5 reapply for every owner, and verifies
that populated audit/platform tables refuse rollback before any drop. The normal
migration launcher remains forward-only; this command targets fresh test containers.

## Production identity qualification in isolation

Build `infrastructure/docker/keycloak/Dockerfile`, then run
`make identity-check ENV=test UV=.build/uv-package/bin/uv`. The test creates its
own PostgreSQL/TLS/two optimized Keycloak replicas and test proxy, checks private
administration, browser PKCE/TOTP, signed access-token trust, failover and encrypted
restore, and cleans only its own containers/network. It compares the original
`nigson-local` IDs/start times/mounts and preserves original volumes. Private test
credentials/tokens/dumps remain ignored under `.build/idp001/`. BP1 operational
tooling has its own locked environment; optional `PROFILE=ai` adds BP5 wire tests
without activating an AI service. See [the runbook](runbooks/keycloak.md).

## Isolated SEC-001 security checks

`make security-check` exercises real owner PostgreSQL, JDBC session one-use/rotation/expiry/revocation/audit and local KMS HTTP protocol. `SEC_IDENTITY_CHECK=true make security-check UV=.build/uv-package/bin/uv` adds the optimized real Keycloak OIDC/MFA/workload/refresh/logout/replica/restore qualification. No AI service is required; `PROFILE=ai` additionally runs existing owned AI wire tests. `make security-infra-check` runs locked backend-free Terraform mock plans. See [session/private trust](runbooks/session-trust.md) for explicit local/test configuration and production refusal rules. Original nigson-local containers/volumes are preserved; only newly generated test namespaces are removed.


## Implemented BFF session routes

COM-SESSI-001 implements /bff/login, /bff/callback, /bff/session and /bff/logout with the same confidential Keycloak adapter in every environment. The local realm BFF client now registers http://localhost:8081/bff/callback and a bounded test-only confidential secret rather than the old framework callback. Profile/subject/audience mappers and closed callback response parameters match the optimized realm. Existing long-lived realm volumes are preserved: creating a fresh isolated local environment imports current configuration; updating an existing realm requires a reviewed client configuration update through Keycloak administration, never overwriting users or roles through a generic seed.

Security remains an explicit configured profile. Enable NIGSON_SECURITY_ENABLED only with all required identity, callback, session encryption, origin, return-path, workload and KMS actor settings; production cannot use the local key/cookie exception. See docs/runbooks/bff-session-endpoints.md. Native endpoint/vendor acceptance uses isolated test configuration and does not supply real identity or deployment approval.
