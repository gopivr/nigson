# Architecture

Nigson uses three Java services, a browser UI and Keycloak. The optional Python
AI service is a later product-phase component. AWS deployment uses ECS Fargate,
PostgreSQL, SNS/SQS, S3 and CloudFront, managed through Terraform.

## Components

| Component | Responsibility |
| --- | --- |
| Commerce | Catalogue, pricing, cart, orders, payments, inventory, fulfillment, rewards and browser BFF |
| Experience | Content, leads, applications, reviews and related customer engagement |
| Integration | Private provider jobs and delivery/messaging integrations |
| Keycloak | Authentication and identity |
| Angular UI | Shell plus marketing, auth, shop, account, admin-core, admin-shop and admin-cms remotes |
| AI | Optional BP5 assistant, with its own data and bounded service access |

## Data and requests

Each owner accesses only its own PostgreSQL schema using separate migration and
runtime roles. Services communicate through scoped APIs and durable events;
there are no cross-owner SQL queries, foreign keys or shared domain entities.
Commerce keeps financial and inventory changes in its local transaction boundary.

The browser uses Commerce BFF sessions backed by PostgreSQL. Keycloak issues
identity tokens to the backend; the UI does not store access tokens or grant roles.
Integration is private and is not a public browser API.

Provider calls happen outside database transactions. Intent is persisted first;
outcomes are reconciled using stable keys. Timeouts retain UNKNOWN state until
authoritative verification. Webhooks and events must tolerate duplication and
out-of-order delivery.

Nigeria/NGN is the launch market. Paystack handles payments; PandaGo handles
delivery. Production uses official provider APIs. Local development uses Paystack
TEST and PandaGo stage; simulators are restricted to isolated automated tests.

## Repository and deployment

Backend Maven modules and Terraform stay at the repository root. Angular sources
and their npm lockfile live under `frontend/`. Root `.github/workflows/` builds
and deploys the same repository revision.

The staging UI is published to a private S3 bucket behind CloudFront. Its release
workflow changes the static origin release path through the existing Terraform
state while retaining backend inputs. Application images, owner migrations and
runtime activation follow the backend release flow.

See [local setup](LOCAL_APPLICATION_GUIDE.md), [AWS deployment](DEPLOYMENT.md),
[API contracts](API_CONTRACT.md), [security](SECURITY.md) and the
[architecture decisions](adr/) for operational detail and decision history.
