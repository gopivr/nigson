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
volumes. Local startup does not seed users or domain records; no reset or prune
is performed by setup or acceptance tests.

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

Local startup imports no example user identities or synthetic business data.
The checked-in identity manifest is consumed only by the isolated automated
cold-start test. That test creates a fresh `nigson-test-*` project and ephemeral
password; its direct-grant client and signed test logins are not available in
the `nigson-local` application environment. Existing local identity and database
volumes are preserved and are not rewritten or cleaned by this workflow.

Public enquiry intake remains disabled until the approved recipient group,
Google reCAPTCHA v3 secret/allowed hostnames, database rate-limit HMAC key, and
trusted proxy CIDRs are configured. Production uses
`NIGSON_LEADS_ANTISPAM_MODE=google-recaptcha-v3` and
`NIGSON_LEADS_RATE_LIMIT_MODE=database`; set the secret and HMAC values through
the secret manager, not source control. The rate-limit key must be shared across
Experience instances and contain at least 32 random bytes, base64-encoded. The
limit defaults to 10 requests per client address per UTC minute. Trust only the
CIDRs of the actual ingress proxies; forwarded client addresses from any other
peer are ignored. Local/test anti-spam mocks remain test-only.

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


Runtime flags default false for rewards/wheel/international/AI/carrier/MCP. Activation validates complete policy plus approval evidence and writes audit. A feature disabled still rejects unauthorized access and reports FEATURE_DISABLED consistently; flags cannot bypass role checks. Missing enabled-provider config causes startup/activation failure, not silent fallback to a mock. Session/financial deadlines must be within documented limits. Use Secrets Manager runtime references and ECS task IAM; no secrets in Terraform state values, image args, Git history, completion reports or .env.example.


## Launch provider configuration and credential replacement

| Group | Required settings |
|---|---|
| Market | LAUNCH_MARKET=NG, LAUNCH_COUNTRY=NG, LAUNCH_CURRENCY=NGN, minor_unit2; tax/returns/book/origin/service-area policies separately versioned |
| Paystack | PAYMENT_PROVIDER=paystack only; runtime PAYSTACK_MODE=LIVE, official PAYSTACK_BASE_URL, PAYSTACK_SECRET_KEY_REF, merchant/account reference, public callback/webhook URLs |
| Twilio | SMS_PROVIDER=twilio, WHATSAPP_PROVIDER=twilio; independent channel enabled flags; runtime TWILIO_MODE=LIVE, official BASE_URL, ACCOUNT_SID/AUTH_TOKEN refs, SMS sender/MessagingServiceSid, WhatsApp sender/ContentSid map, exact status/inbound public URLs |
| Keycloak | KEYCLOAK_IMAGE_DIGEST, dedicated DB/secret refs, realm issuer, public hostname/private admin hostname, trusted proxy settings, BFF/workload client secret refs, MFA/realm/client/audience policy |

Application Compose defaults use official live provider hosts and require real
credentials from an ignored local secret file or secret manager. No provider
request is routed to a mock service. Isolated automated tests may use loopback
HTTP simulators and synthetic fixtures. Mode never skips business invariants or
callback signature verification.

Every application profile rejects mock/sandbox targets or dummy credentials for
enabled financial/carrier/messaging features. Missing enabled-provider config
fails validation rather than silently falling back. Live provider qualification
requires explicit approval and controlled accounts/recipients; no provider
requests are made by ordinary tests. A live environment with features disabled
may start for infrastructure checks but cannot claim completed live
checkout/fulfillment/messages. Keycloak production configuration is required
for production identity.


# Master section31: Docker

Generate a multi-stage Dockerfile for each Java/Python deployable during implementation. Java build uses Maven wrapper/Java25, runtime JRE25 minimal supported image pinned by digest, non-root UID10001, read-only root filesystem where feasible, temp volume, graceful SIGTERM and JVM container memory awareness. Python build creates a locked isolated environment, copies only package/runtime dependencies, non-root UID10001, uvicorn bounded workers and async pools. No embedded credential or package-download at runtime. Verify ARM compatibility before switching task architecture.

Health: GET /actuator/health/liveness and /actuator/health/readiness Java; GET /health/live and /health/ready Python. Liveness proves process/event-loop responsiveness, not third-party payment/AI availability. Readiness checks essential owned DB/schema initialization and safe configuration; external email/model outages do not restart otherwise healthy core services. Worker health includes its ability to poll/commit work; probe binds private interface and no public route. Container healthcheck uses the actual port and shell-free minimal command available in image; ECS/ALB health checks duplicate only necessary signals.

Local docker-compose.yml baseline: PostgreSQL18 pgvector-capable image
(extension used only AI profile), LocalStack SNS/SQS/S3/SES infrastructure
emulation, Keycloak local realm, Mailpit utility, optional OTel collector.
Application business-provider traffic bypasses Mailpit and uses official live
endpoints. Full-app profile starts commerce8081, experience8082, worker8083
private, AI8084 only ai profile; dependencies ports localhost only. Valkey is
optional cache profile, not default. No Kafka/OpenSearch/Kubernetes/Helm
services. DB healthcheck plus one-shot migrations precede application
readiness. Persistent named volumes are never deleted by default stop.
LocalStack IAM emulation does not replace live staging IAM/security tests.


## Live provider Compose and production identity

other configured live APIs. Required credentials are supplied out of band;
starting the app without them fails closed. Compose does not start business
provider mocks or route app requests to Mailpit. LocalStack and Mailpit remain
local infrastructure utilities only. Stateful HTTP simulators and callback
scenarios are confined to automated tests on isolated loopback/test networks;
tests prohibit external paid network access. Production builds exclude mock
controllers, images, dummy credentials and test admin routes.

Keycloak image is pinned/optimized in production, supported external PostgreSQL, private management/health/admin, TLS/proxy/hostname and cluster settings; local start-dev is local only. Do not reuse Keycloak development filesystem DB or default admin credentials for release.


# Master section36: Test Data

Fixtures live at service src/test/resources/fixtures and shared tests/fixtures; deterministic UUIDs, fixed Clock `2026-10-04T00:00:00Z`, seeded randomness only in tests, synthetic `example.invalid` emails and no real staff/customer data. The local Keycloak realm imports no example users; test identities are provisioned only in an isolated automated test namespace.

Catalogue: three categories, two brands, five products/ten SKUs, one initial location, some out-of-stock/ineligible/archived variants. Default synthetic NG market countryNG/NGN minor-unit2 reflects confirmed launch metadata; addresses/users/tax/rates remain local/test-only; tests add a currency with3 decimals, fractional SKU, international-ineligible product, two price tiers and ambiguous delivery configurations that must fail. Tax tests use a clearly synthetic configured10 percent rate, not a legal tax recommendation. Promotion/reward fixtures use finite test budgets and verified policy revision; prod features remain disabled.

Provider fixtures cover Paystack charge.success, valid/invalid HMAC, duplicate object IDs, currency/amount mismatch, unknown reference, late success and provider HTTP timeout; refund fixtures cover processed/failed/pending and lost-response uncertainty. Sheet fixture has stable application ID column, CV filename inert text and no real resume. AI set has at least200 versioned cases divided facts/discovery/unsupported/private-scope/injection/withdrawal/provider failure. Include approved answer/source IDs and scoring rubric; test mock output cannot establish live model correctness.

Never seed arbitrary production ADMIN or a working merchant credential. Provision first administrator through a controlled subject assignment/runbook with audit and approved identity. Fixtures are segregated by Liquibase local/test context and explicit APP_ENV checks.


## Approved launch fixtures and mock scenarios



# Master section39: Local Development Setup

Implementation must generate README.md, .env.example, docker-compose.yml, Makefile and idempotent setup scripts. Supported prerequisite: Docker Compose, Java25, Maven wrapper, Python3.12/uv only for ai profile; shell commands work from repository root.

```bash
cp .env.example .env
make local-up
make migrate ENV=local
make run-core
make verify
# only when reaching EP12/BP5
make local-up PROFILE=ai
make run-ai
```

`local-up` starts required dependencies and waits on bounded health checks; it creates local SNS subscriptions/S3 buckets and imports Keycloak clients/roles without example identities. `migrate` executes each owner role/master, fails on any schema drift and does not use Hibernate DDL. Local startup seeds no users or business data. `run-core` starts Java services in separate processes/Compose app profile; reports URLs/health checks and clean stop handling. User login uses the configured local OIDC registration flow; direct API tests obtain real signed access tokens in isolated test projects. Do not add a universal insecure bypass header.

The local application profile calls configured live business-provider APIs.
credentials and approved account metadata in the ignored `.env`; do not copy
test fixture credentials into an application runtime. Provider simulators are
available only to isolated automated tests. LocalStack remains for infrastructure
emulation and is not a business-provider fallback.

Local services binds: BFF/core http://localhost:8081, experience8082, worker8083 localhost/private only, AI8084 optional, PostgreSQL5432 localhost, LocalStack4566, Keycloak8180, Mailpit8025. Origin URLs configurable and not compile-time constants. Use named volumes and separate test DB. `.env` is gitignored. LocalStack credentials are emulator-only; enabled business-provider integrations require approved live credentials. Local HTTP cookie/issuer exceptions are profile-limited. Health/smoke checks confirm real persistence and identity flow before showing ready.


## Isolated provider test setup

```bash
make provider-contract-test PROVIDER=all MODE=mock
make keycloak-config-check ENV=local
```

Provider contract tests start simulators in an isolated test harness; they do
not start the local application against mock or sandbox business-provider
endpoints. Do not configure or control simulators through the local Compose
stack. `make provider-live-check ENV=stage` requires approved live account,
credential, sender, and service configuration and may make real provider
requests; do not run it automatically.


# Master section40: Developer Experience

| Command | Behavior |
|---|---|
| make build | Maven package skip-tests only when explicitly asked; normal verify includes tests; AI locked build when enabled |
| make test / verify | Required unit/integration/security/contracts with real DB/emulator |
| make format / lint | Spotless plus Ruff/mypy and contract/schema lint |
| make local-up / local-down | Bounded start / graceful stop; never deletes volumes |
| make run-core / run-ai | Environment-validated processes, AI only at enabled phase |
| make migrate ENV=local | Owner Liquibase masters; each role used separately |
| make cold-start | Isolated test identities and disposable test volumes; preserves local volumes |
| make reset-db ENV=local CONFIRM=reset-local | Explicit destructive local operation only, refuses remote/prod URLs and preserves backup option |
| make contracts | Regenerate OpenAPI and schemas; diff checked and reviewed |
| make load-test / restore-test | Realistic staging-safe synthetic workloads and isolated restore |
| make status-check | Verify task/report/status consistency; never infer test success |

Scripts use strict shell error handling, quote paths/variables, bounded readiness waits and clear exit codes. Do not repurpose HOME/system variables. Execution scripts must be safe to rerun and must not mask failures. Document exact generated command forms in LOCAL_DEVELOPMENT.md and task completion reports. Avoid requiring manual table edits, guessed ports or privileged cloud credentials for local development.


| Added command | Required behavior |
|---|---|
| make provider-contract-test PROVIDER=all MODE=mock | Run provider adapter contracts against isolated test simulators; no provider sandbox/live calls. |
| make keycloak-config-check ENV=<env> | Realm/client/audience/MFA/hostname/admin/prod profile validation. |
| make provider-live-check ENV=stage | Explicit live-provider qualification; use approved credentials/accounts and record results honestly. |

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

`make configuration-check` and `make catalogue-check` execute isolated owner migration/native/HTTP qualifications with dummy fixtures and preserve original local resources. Catalogue adds representative10k product/20k SKU source-read measurements; this is not the30m staging release/capacity test. Explicit local/test NIGSON_PUBLIC_ASSET_BASE_URL may use a synthetic HTTPS .test target for deterministic URL qualification; no live CDN or provider account is implied.

`make files-check` runs isolated PostgreSQL owner migrations plus genuine TLS/SigV4 S3 HTTP using stateful synthetic fixture bytes. `ENV=test python3 scripts/files/schema_upgrade.py` separately proves previous BP1 upgrade/checksum preservation, empty Liquibase rollback/reapply and populated file-transfer rollback refusal. Set the pinned JDK/local Unix Docker endpoint as for catalogue-check. Fixture CA/key, signed capabilities and private control tokens stay under ignored .build/files001; never use them for live storage. NIGSON_FILES_ENABLED is false until explicit storage inputs are configured; disabled uploads create no intent. See docs/runbooks/files.md.

`make integration-jobs-check` qualifies the commerce operations job proxy against isolated owner PostgreSQL and actual local Keycloak-style token/JWKS, KMS SDK and stateful private worker HTTP. Current roles, MFA, CSRF, stale versions, uncertain acknowledgements, durable command replay, audit rollback and races are verified; this does not qualify the unimplemented integration worker or live vendors. Integration job access defaults disabled and requires an explicit private target. See docs/runbooks/integration-jobs.md, including the initial job-version discovery contract gap.

Notification acceptance: `make notification-check ENV=test` supplies isolated owner PostgreSQL and stateful loopback integration JWT/KMS/template fixtures, preserving other Docker workloads. Production notification template commands are disabled until explicit `NIGSON_NOTIFICATION_TEMPLATES_ENABLED=true` and an approved private `NIGSON_INTEGRATION_BASE_URL` are configured. Context and delivery APIs still require the compiled integration workload scopes; they never send a message.

`make inventory-check ENV=test` qualifies the owned stock operations and local transaction port with isolated PostgreSQL and actual BFF HTTP. It preserves original Docker resources. Synthetic fixture stock/order/payment rows are test-only and do not authorize actual warehouse, initial stock, tax, prices or provider accounts.

`make market-check ENV=test` qualifies exact API-029/031/162/169 and owned policy/race/rollback/BFF behavior against isolated local containers. Synthetic policy/provider proofs confer no live activation.

`make nigeria-check ENV=test` applies approved disabled NG/NGN2/destination baseline and validates actual HTTP Paystack kobo/market behavior. `ENV=test python3 scripts/nigeria/migration.py` qualifies previous-version upgrade/policy preservation and actual Liquibase rollback/conflict guards. Metadata comes from BP1 migrations; identity-only `seed` supplies no tax/prices/routes or provider approval. Existing .env.example launch facts already NG/NGN and remain unchanged.
