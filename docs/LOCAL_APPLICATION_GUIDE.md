# Run Nigson locally

This guide starts the Nigson backend on your machine using Docker Compose. PostgreSQL, Keycloak, LocalStack, and Mailpit are local infrastructure services; business-provider simulators are not started. Application requests use configured live provider APIs or fail closed when live configuration is missing. Nigson is a backend repository; this workflow does not start a storefront UI.

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

The template contains local infrastructure credentials and empty provider secret settings. Provider credentials and accounts must be approved and configured before enabling provider-backed features. `.env` is gitignored.

Start local dependencies, apply the owner database migrations, then build and start the backend:

```sh
make local-up
make migrate ENV=local
make run-core
make local-status
```

`run-core` reapplies migrations safely, builds the Commerce, Experience, and Integration images, and starts the services. Commerce contains the BFF and business transaction boundary. Integration remains an internal worker; its local port is bound to loopback for development tools.

Local startup creates no example Keycloak users or synthetic business records. Use the registration flow only after configuring the complete local BFF/security profile. Existing database and identity volumes are preserved and may still contain data from earlier local runs; startup does not delete or rewrite that data.

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

The default Compose configuration leaves application identity/security inputs unset. Commerce's foundation profile permits health checks and denies its API routes; BFF sign-in stays closed, and `/bff/session` or `/bff/login` can return `401 AUTH_REQUIRED`. The workbench checks session readiness before starting Keycloak sign-in and explains this configuration gate. Follow [the session trust runbook](runbooks/session-trust.md) for the complete BFF profile; For local identity setup after `make local-up`, run `make local-app-config`; setting only `NIGSON_SECURITY_ENABLED=true` is insufficient. The workbench does not bypass authorization or fabricate data. No local identities, catalogue, prices, taxes, carrier data, campaigns, or policies are seeded.

For the MFE on `http://localhost:4200`, the ignored local `.env` may set `NIGSON_SECURITY_ENABLED=true`, `NIGSON_BFF_CALLBACK=http://localhost:4200/bff/callback`, `NIGSON_BFF_ALLOWED_ORIGINS=http://localhost:4200,http://localhost:8090`, and `NIGSON_BFF_RETURN_PATHS=/,/account,/shop,/checkout`. The callback uses the same browser hostname as the MFE so the pending BFF cookie returns to Commerce. Compose passes through the remaining session-encryption, local KMS actor-signing, workload, and pinned actor-public-key settings; supply them only from the local session-trust runbook. The local Keycloak import configures a separate registration service account. The MFE signup form posts to `/bff/register`; Commerce creates the Keycloak identity and never stores the password. After registration, continue through the regular sign-in button. Existing persistent realms are not overwritten by realm import; apply the callback URI and registration client/service-account mapping without deleting identity volumes.

The sign-in button starts the BFF authorization-code/PKCE flow and opens the local Keycloak page at `http://localhost:8180`. That page uses Nigson branding but remains hosted by Keycloak, so the MFE never receives the password. Compose mounts the checked-in theme read-only; the `nigson-local` realm selection persists in the existing identity database. Use `http://localhost:4200` for the MFE; if it is opened at `127.0.0.1:4200`, the sign-in action switches the BFF request to `localhost` to match the configured callback and preserve the host-only pending-session cookie. The BFF retains its server-side session and CSRF protections.

If an old `/bff/callback` page shows `AUTH_REQUIRED`, return to the MFE and start sign-in again; authorization codes and pending state are one-use. The persistent Keycloak `nigson-bff` client secret must match `NIGSON_BFF_CLIENT_SECRET` from `.env`/Compose. If you change that setting, update the local Keycloak client's secret as well; realm import does not overwrite an existing realm.

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

Conversation setup only enables the anonymous session capability. Sending a message also requires an approved AI policy, prompt revision, and live provider configuration in the AI owner schema; missing configuration fails closed. This guide does not supply approval, model, rate, budget, or retention values. Integration and internal workload endpoints remain private.

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

This stops the Compose containers and preserves named volumes, including local databases and any pre-existing records. Restart with `make local-up`; do not use volume deletion as a routine shutdown step. Inspect running containers with `make local-status`.

## Troubleshooting

- If startup reports a missing local setting, copy the current `.env.example` again and verify the required local-only values. Local uses real Paystack TEST and PandaGo stage credentials. Dummy Termii/SMTP credentials fail on access; production requires real LIVE credentials.
- If Docker is unavailable, start Docker Desktop and rerun `make local-config-check` before starting services.
- If migrations fail, keep the full error output and inspect the owner-specific migration logs with `docker compose --project-name nigson-local --env-file .env --file docker-compose.yml --profile app logs migrate-commerce migrate-experience migrate-integration`. Fix the cause and rerun `make migrate ENV=local`; do not drop owner schemas or clear Liquibase checksums to bypass drift.
- If a port is occupied, set a different loopback host port in `.env` or the shell environment, then run `make local-config-check` again.

For configuration details, fixture behavior, cold-start checks, and feature commands, see [Local Development](LOCAL_DEVELOPMENT.md). Production and stage deployment procedures are separate and require reviewed environment inputs; this local workflow does not qualify cloud deployment or live providers.
