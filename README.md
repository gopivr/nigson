# Nigson backend

This monorepo implements the Nigson platform from
[BACKEND_IMPLEMENTATION_MASTER.md](BACKEND_IMPLEMENTATION_MASTER.md), specification
1.1.0, architecture source 1.0 plus
[ADR-008](docs/adr/ADR-008-confirmed-nigeria-launch-providers.md).
Read [AGENTS.md](AGENTS.md) and [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)
before working. Task acceptance, dependencies and release composition are in
master sections 49, 51, 54 and 46 respectively.

Commerce includes the Java BFF and atomic financial/stock boundary. Experience
and private integration are separate Java services. Python AI begins at BP5.
Each owner has its own PostgreSQL schema and roles. Keycloak supplies identity;
ECS Fargate and SNS/SQS supply deployment and messaging. Launch is Nigeria/NGN,
Paystack, FedEx and email/Twilio SMS/WhatsApp.

The supplied `Nigson_Backend_Implementation_Blueprint/` is preserved unchanged.
It originally contained only specification documents, with no application code,
backend builds, provider qualification or completed implementation tasks.
Root documents are authoritative for current work. `.gitkeep` files reserve the
specified directories and do not implement services, migrations or infrastructure.

Run `python3 scripts/check_repository.py` or `make status-check` to check the
repository structure and task registry. These are repository checks, not a
backend build or provider qualification. All executable build/local development
commands will be documented when their roadmap tasks implement them. See
[LOCAL_DEVELOPMENT.md](docs/LOCAL_DEVELOPMENT.md) for the intended command surface
and [session reports](docs/implementation/) for commands actually executed.

Toolchain target: Java 25, Spring Boot 4.1.1, Modulith 2.1.1, Maven wrapper,
PostgreSQL 18 and Python 3.12/uv for the AI profile only. Exact tested locks belong
to FOUND-002. Genuine provider testing requires separately supplied credentials,
accounts, senders, approved templates and routes; missing credentials do not
block offline adapters and stateful mocks. No tax or campaign policy is inferred.
