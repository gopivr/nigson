# Nigson Codebase and Integration Configuration Guide

Short guide to where to start reading, where integration settings live, and what must change before a real provider can be used outside local development. Status reflects repository documentation reviewed on 7 November 2026; it is not proof of a production deployment.

## Read the codebase in this order

1. `README.md` — project layout, local workflow, toolchain.
2. `AGENTS.md` — implementation constraints and rules.
3. `IMPLEMENTATION_STATUS.md` — current task status and blockers. Read the linked implementation report for evidence.
4. `BACKEND_IMPLEMENTATION_MASTER.md` — authoritative specification and release composition.
5. `docs/ARCHITECTURE.md`, `docs/API_CONTRACT.md`, and `docs/adr/` — design, contracts, and decisions.
6. Source: `services/commerce/`, `services/experience/`, `services/integration/`, `services/ai/`; shared Java code is in `shared/`.
7. Tests and provider fixtures: `services/*/src/test/`, `tests/`, and `contracts/`.

The supplied `Nigson_Backend_Implementation_Blueprint/` is historical reference material. Current source and root-level documents are authoritative.

## Where configuration lives

| What | Main location |
|---|---|
| Local environment values | `.env` (ignored; do not commit); starting template: `.env.example` |
| Local service wiring and default endpoints | `docker-compose.yml` |
| PandaGo quote-only TEST overlay | `docker-compose.pandago.yml` |
| Optional partner TEST settings | `docker-compose.partner-test.yml` |
| Java service property-to-environment-variable bindings | `services/*/src/main/resources/application.yml` |
| Provider behavior and opt-in switches | `services/*/src/main/java/` configuration classes; search for `@ConditionalOnProperty` |
| API/provider contracts | `contracts/` |
| Database schema changes | `database/changelog/` and owner service changelogs |
| Stage/prod infrastructure inputs | `infrastructure/terraform/environments/{stage,prod}/` |
| Production secret *names and access infrastructure* | `infrastructure/terraform/modules/secrets/`; secret values are provisioned separately |
| Stage deployment workflow | `.github/workflows/deploy-stage.yml` |

Compose supplies defaults for local development. Java `application.yml` files generally resolve settings from environment variables. A production environment must supply explicit values; do not copy local Compose defaults into production.

## Integrations: local routing versus real-provider readiness

| Integration | Local setting / route | What is still needed for a real environment |
|---|---|---|
| Paystack | Commerce defaults to `PAYSTACK_MODE=MOCK` and `http://paystack-mock:8080`. Stateful mock is in the `provider-mocks` Compose profile. | Use the correct TEST or LIVE endpoint and a separately provisioned merchant key; verify webhook configuration and merchant qualification. Partner TEST config exists, but does not mean checkout is production-ready. |
| FedEx | Integration Compose sets `FEDEX_MODE=MOCK` and routes to `fedex-mock`. | Qualified account, credentials, origin, services/routes and approval. The normal local stack is not a live FedEx configuration. |
| Twilio SMS/WhatsApp | Integration Compose sets `TWILIO_MODE=MOCK` and routes to `twilio-mock`. | Account credentials, sender/WhatsApp setup, approved templates and callback verification. |
| Email | Local email uses `EMAIL_MODE=MOCK` and Mailpit. | Approved mail sender/domain and production delivery configuration. |
| AWS services | Local events and related AWS-compatible functions use LocalStack endpoints and synthetic local credentials. | Real AWS resources, workload IAM, networking, queues/topics and policies. |
| PandaGo | Fee and historical delivery-time estimates use `POST /orders/fee` and `POST /orders/time`; the short-lived `POST /quotes` fee remains the actual bound checkout delivery fee. A separate Compose overlay opts into TEST quotes; normal local Compose keeps carrier quotes off. | Supply approved TEST credentials, outlet/account fingerprint and unexpired qualification inputs to use the overlay. Estimate inputs are the Commerce-calculated merchandise subtotal and coordinates on a saved address. Booking, status/tracking and reconciliation remain separate incomplete workflows; this is not production qualification. |
| Termii / Dellyman | Optional partner TEST overlay and adapter ports exist. | Provider account/sender/pickup/service inputs and binding into owner workflows remain incomplete. No deployment is claimed. |
| Identity | Local Keycloak is a real local Keycloak instance, not a provider API mock. | Production Keycloak deployment and its security, TLS, database, backup and operations setup still need release qualification. |

Mocks are deliberate local/test substitutes, not evidence that a provider call happened. Keep dummy credentials paired only with mock endpoints. Never switch a mock URL to a real provider while retaining dummy credentials.

## Using real configuration in stage/production

1. Use the environment-specific Terraform under `infrastructure/terraform/environments/` to define infrastructure and explicit service inputs. Review plans before applying.
2. Put credentials in the environment's approved secret store (the Terraform secrets module creates AWS Secrets Manager entries; values are provisioned outside Terraform). Pass only the required secret references to the correct service/task role. Do not put secret values in Git, Terraform variables/state, Compose files, or logs.
3. Set provider mode, approved endpoint, account binding, feature switches, callback URLs, and expiry/approval references explicitly for that environment. Require production to fail closed; do not allow fallback to mocks.
4. Qualify TEST credentials and webhooks first. Verify provider account, allowed routes/services, sender/template approvals, idempotency/reconciliation, and monitoring before any LIVE switch.
5. Deploy an immutable, reviewed release and verify migrations, health, security, and rollback. The repository has a reviewed **stage** workflow using GitHub OIDC. A completed production deployment/qualification is not established by the presence of `environments/prod` Terraform files.

## What is pending

- Current status marks `RUNTIME-MOCK-REMOVAL` and `PANDAGO-001` in progress. When carrier quotes are enabled and qualified, checkout requests a carrier quote before payment and displays PandaGo fee/ETA estimates when returned; the bound quote fee, not the estimate, is used for payment.
- Genuine provider qualification/activation remains pending for carrier, messaging and partner integrations; exact credentials and account approvals must come from their owners.
- PandaGo real account/key qualification and its end-to-end booking, UNKNOWN reconciliation, status and tracking workflow are incomplete. Carrier quotes remain off in normal Compose and require explicit, approved TEST/LIVE configuration.
- Dellyman/Termii owner-workflow bindings and provider account details are incomplete.
- Production infrastructure inputs, secret values, provider approvals and release evidence are environment-specific inputs, not values to invent or reuse from local development.

For the latest evidence and exact task-by-task blockers, use `IMPLEMENTATION_STATUS.md` and the linked files under `docs/implementation/`. A passing mock test verifies adapter behavior against a mock; it does not qualify a real account or prove production readiness.
