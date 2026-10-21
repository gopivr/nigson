# Run Nigson locally

This guide starts the Nigson backend on your machine using Docker Compose. It uses local PostgreSQL, Keycloak, LocalStack, Mailpit, and stateful provider mocks. It does not connect to live payment, carrier, SMS, WhatsApp, or cloud accounts. Nigson is a backend repository; this workflow does not start a storefront UI.

## Requirements

- Docker Desktop or another local Docker Engine with Docker Compose v2.
- Java 25 for builds and tests. The checked-in Maven wrapper selects the project Maven version.
- A terminal at the repository root.

Python/uv is only needed for the optional AI build and test commands. The core Java services run in containers.

## First run

Copy the local-only environment template and validate the Compose configuration:

```sh
cp .env.example .env
make local-config-check
```

The template contains synthetic local passwords and dummy provider credentials. Keep them pointed at the Compose mocks. Do not replace a mock URL with a real provider URL while keeping dummy credentials. `.env` is gitignored.

Start local dependencies and mocks, apply the owner database migrations, then seed test identities:

```sh
make local-up
make migrate ENV=local
make seed ENV=local FIXTURE_PROFILE=foundation
```

The seed command creates deterministic test-only Keycloak identities and verifies real signed local logins. It does not create product catalogues, prices, tax rules, provider accounts, or commercial approvals.

Build and start the Java backend services:

```sh
make run-core
make local-status
```

`run-core` reapplies migrations safely, builds the Commerce, Experience, and Integration images, and starts the services. Commerce contains the BFF and business transaction boundary. Integration remains an internal worker; its local port is bound to loopback for development tools.

## Local endpoints

| Component | Address | Health check |
|---|---|---|
| Commerce API/BFF | `http://localhost:8081` | `/actuator/health/readiness` |
| Experience API | `http://localhost:8082` | `/actuator/health/readiness` |
| Integration worker | `http://localhost:8083` (loopback only) | `/actuator/health/readiness` |
| Keycloak | `http://localhost:8180` | `GET /realms/nigson-local/.well-known/openid-configuration` |
| PostgreSQL | `localhost:5432` | Compose health check |
| LocalStack | `http://localhost:4566` | Compose health check |
| Mailpit web UI | `http://localhost:8025` | — |
| Mailpit SMTP | `localhost:1025` | — |

Check service readiness with:

```sh
curl -fsS http://localhost:8081/actuator/health/readiness
curl -fsS http://localhost:8082/actuator/health/readiness
curl -fsS http://localhost:8083/actuator/health/readiness
```

All published host ports bind to `127.0.0.1`. Ports can be changed with the corresponding variables in `docker-compose.yml` (for example, `COMMERCE_PORT` or `KEYCLOAK_PORT`). The Integration worker is private application infrastructure and has no public route.

## Interactive API and AI demo

Start the browser workbench after `make run-core`:

```sh
python3 scripts/local/demo_server.py
```

Open <http://localhost:8090>. The static page embeds operations from the Commerce, Experience, Integration, and AI OpenAPI contracts. It provides readiness checks, an editable HTTP request/response console, and a conversation panel for the local AI assistant. Use the API operation descriptions to provide required bodies, path values, idempotency keys, and current `If-Match` versions.

The small Python server serves the static file and proxies only to local Commerce, Experience, and AI ports. It accepts requests from its exact localhost origin, forwards an explicit header allowlist, preserves Commerce BFF cookies, and never forwards browser identity assertions. Integration workload APIs appear as private catalog entries but cannot be called through the demo. The proxy is a local development helper, not a deployment component.

The default Compose configuration leaves application identity/security inputs unset. Commerce's foundation profile permits health checks and denies its API routes; BFF sign-in stays closed, and `/bff/session` or `/bff/login` can return `401 AUTH_REQUIRED`. The workbench checks session readiness before starting Keycloak sign-in and explains this configuration gate. Follow [the session trust runbook](runbooks/session-trust.md) for the complete BFF profile; setting only `NIGSON_SECURITY_ENABLED=true` is insufficient. The workbench does not bypass authorization or fabricate data. Seeded identity users do not imply seeded catalogue, price, tax, carrier, campaign, or policy configuration.

To exercise implemented APIs before configuring the interactive BFF profile, run their owner acceptance targets. These start real HTTP applications against isolated test databases and stateful local mocks; they do not use or erase the `nigson-local` development volumes. For example:

```sh
make session-check
make catalogue-check
make pricing-check
make cart-check
make order-check
```

Other focused targets include `make party-check`, `make inventory-check`, `make fulfillment-check`, `make payment-check`, `make files-check`, `make notification-check`, `make authorization-check`, and `make audit-check`. `make verify` runs the repository verification suite. Each target has its prerequisites and evidence path in the Makefile/script and task runbook; use the target for the API owner you want to test. The browser workbench is a request console, not an API simulator: in the default profile, 401/403 responses are expected for gated routes.

The AI container is optional. The default Compose service deliberately leaves its conversation settings unset, so anonymous conversation creation returns `422 FEATURE_DISABLED` until you configure them. Add the following to your ignored local `.env` file using locally approved settings, then recreate the AI container:

```dotenv
NIGSON_AI_ANON_TOKEN_KEY=<base64url key containing at least 32 random bytes>
NIGSON_AI_CHECKPOINT_KEY=<base64url encoding of exactly 32 random bytes>
NIGSON_AI_CONVERSATION_TTL_SECONDS=<approved conversation lifetime in seconds>
NIGSON_AI_CHECKPOINT_TTL_SECONDS=<approved checkpoint lifetime in seconds>
NIGSON_AI_DEFAULT_LOCALE=<configured locale, such as en-NG only if approved>
NIGSON_AI_ALLOWED_MARKETS=<comma-separated configured market codes>
```

The conversation TTL must be 60–86400 seconds, and the locale and requested market (`NG` in the demo) must be configured. Do not commit real keys. Apply BP5 migrations and restart the AI profile after setting these values:

```sh
make local-up PROFILE=ai
make migrate ENV=local PROFILE=ai
make run-ai PROFILE=ai
```

Conversation setup only enables the anonymous session capability. Sending a message also requires an approved AI policy and prompt revision in the AI owner schema. The deterministic local provider never contacts a live model; this guide does not supply approval, model, rate, budget, or retention values. Integration and internal workload endpoints remain private.

## Optional AI profile

The AI runtime is optional and belongs to BP5. Only use it when working on BP5 tasks. Set `SCHEMA_RELEASE=BP5` in `.env`, then start the AI dependencies, migrate its owner schema, and run the service:

```sh
make local-up PROFILE=ai
make migrate ENV=local PROFILE=ai
make run-ai PROFILE=ai
curl -fsS http://localhost:8084/health/ready
```

This profile uses the local vector database. It does not activate live model calls; approved model, policy, rate, and budget configuration is still required.

## Build and test

Run the repository checks and Java build/tests with:

```sh
make verify
```

For the optional Python AI package:

```sh
make verify PROFILE=ai
```

Feature-specific acceptance commands are listed in the root `Makefile` and their relevant runbooks under `docs/runbooks/`. Most native PostgreSQL acceptance checks require Docker. They use isolated test resources and should preserve the named `nigson-local` development data.

## Stop the local stack

```sh
make local-down
```

This stops the Compose containers and preserves named volumes, including local databases and mock state. Restart with `make local-up`; do not use volume deletion as a routine shutdown step. Inspect running containers with `make local-status`.

## Troubleshooting

- If startup reports a missing local setting, copy the current `.env.example` again and verify the required local-only values. Do not add real provider credentials to make local mocks work.
- If Docker is unavailable, start Docker Desktop and rerun `make local-config-check` before starting services.
- If migrations fail, keep the full error output and inspect the owner-specific migration logs with `docker compose --project-name nigson-local --env-file .env --file docker-compose.yml --profile app logs migrate-commerce migrate-experience migrate-integration`. Fix the cause and rerun `make migrate ENV=local`; do not drop owner schemas or clear Liquibase checksums to bypass drift.
- If a port is occupied, set a different loopback host port in `.env` or the shell environment, then run `make local-config-check` again.

For configuration details, fixture behavior, cold-start checks, and feature commands, see [Local Development](LOCAL_DEVELOPMENT.md). Production and stage deployment procedures are separate and require reviewed environment inputs; this local workflow does not qualify cloud deployment or live providers.
