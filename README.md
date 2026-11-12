# Nigson backend

This monorepo implements the Nigson platform from
[BACKEND_IMPLEMENTATION_MASTER.md](BACKEND_IMPLEMENTATION_MASTER.md), specification
1.1.0, architecture source 1.0 plus
[ADR-008](docs/adr/ADR-008-confirmed-nigeria-launch-providers.md) and
[ADR-026](docs/adr/ADR-026-live-provider-runtime.md).
Read [AGENTS.md](AGENTS.md) and [IMPLEMENTATION_STATUS.md](IMPLEMENTATION_STATUS.md)
before working. Task acceptance, dependencies and release composition are in
master sections 49, 51, 54 and 46 respectively.

Commerce includes the Java BFF and atomic financial/stock boundary. Experience
and private integration are separate Java services. Python AI begins at BP5.
Each owner has its own PostgreSQL schema and roles. Keycloak supplies identity;
ECS Fargate and SNS/SQS supply deployment and messaging. Launch is Nigeria/NGN,
Paystack payments, PandaGo delivery, and configured messaging/email.

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

Toolchain: Oracle JDK 25.0.2+10-LTS-69, Spring Boot 4.1.1, Modulith 2.1.1,
checksum-pinned Maven 3.10.0 wrapper, PostgreSQL 18 target and Python 3.12.12/uv
0.9.3 for the AI profile only. See [DEPENDENCY_LOCK.md](docs/DEPENDENCY_LOCK.md)
for tested locks, compatible security patches and verification commands.
Set `JAVA_HOME` to that JDK, then run `make verify`. `MAVEN_BIN` can select an
installed Maven 3.10.0 or newer when needed. `make verify PROFILE=ai`
additionally installs the locked Python dependencies and builds/tests the BP5
package foundation; core builds never require Python. `UV` can select your
installed uv executable. Build caches stay in `.build/`. These bootstraps contain
no domain endpoints or AI assistant implementation. Application runtimes require
real provider credentials, accounts, senders, approved templates and routes;
missing configuration fails closed. Isolated automated tests retain stateful
HTTP simulators. No tax or campaign policy is inferred.

The local application workflow is `make local-up`, `make migrate ENV=local`,
then `make run-core`. Local startup imports no example user identities or
synthetic business data. Follow
the [Run Nigson locally](docs/LOCAL_APPLICATION_GUIDE.md) guide and its
[interactive API workbench](docs/demo/index.html) for local testing. The guide
includes the complete first-run sequence, service addresses, health checks and
shutdown steps.
`make cold-start` qualifies that workflow twice on fresh isolated test volumes
with test-only identities and preserves the running `nigson-local` stack. Owner migrations and non-root
application Docker runtimes, owner schemas and isolated test provider mocks are
implemented; application provider calls require live configuration. See the local development
guide for guards, fixtures and optional vector/AI qualification.

The [platform observability runbook](docs/PLATFORM_OBSERVABILITY.md) documents safe
errors, transactional audit, correlation, owned SDK telemetry, bounded ADOT exports
and CloudWatch alarms. Run `make observability-check`, `make observability-infra-check`
and `make collector-config-check` for the isolated offline qualifications. Genuine
cloud delivery and production activation remain separately gated.

Carrier integration uses PandaGo. Retired carrier adapters and credentials have been removed.
