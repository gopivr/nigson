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

Toolchain: Oracle JDK 25.0.2+10-LTS-69, Spring Boot 4.1.1, Modulith 2.1.1,
checksum-pinned Maven 3.9.11 wrapper, PostgreSQL 18 target and Python 3.12.12/uv
0.9.3 for the AI profile only. See [DEPENDENCY_LOCK.md](docs/DEPENDENCY_LOCK.md)
for tested locks, compatible security patches and verification commands.
Set `JAVA_HOME` to that JDK, then run `make verify`. `make verify PROFILE=ai`
additionally installs the locked Python dependencies and builds/tests the BP5
package foundation; core builds never require Python. `UV` can select your
installed uv executable. Build caches stay in `.build/`. These bootstraps contain
no domain endpoints or AI assistant implementation. Genuine provider testing requires separately supplied credentials,
accounts, senders, approved templates and routes; missing credentials do not
block offline adapters and stateful mocks. No tax or campaign policy is inferred.

The foundation local workflow is `make local-up`, `make migrate ENV=local`,
`make seed ENV=local FIXTURE_PROFILE=foundation`, then `make run-core`.
`make cold-start` qualifies that workflow twice on fresh isolated test volumes
and preserves the running `nigson-local` stack. Owner migrations and non-root
application Docker runtimes are executable; business schemas, APIs and stateful
provider mocks remain pending in DATA-001 and MOCK-001. See the local development
guide for guards, health URLs and optional vector/AI qualification.
