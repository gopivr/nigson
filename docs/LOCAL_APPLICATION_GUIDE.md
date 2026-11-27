# Run locally

## Requirements

Docker with Compose v2, Java 25, Python 3, and Node 24.15.0. The Maven wrapper
and frontend lockfile select the remaining build dependencies. Python/uv is needed
for the optional AI profile; it is not needed to build the Java services.

## Backend

From the repository root:

```sh
cp .env.example .env  # only if .env does not already exist
make local-config-check
make local-up
make run-core
make local-status
```

Edit `.env` for your local configuration. `run-core` applies migrations, builds
images and starts Commerce, Experience and Integration. It preserves existing
local database and identity volumes.

For sign-in, configure the local security/BFF settings using `make local-app-config`
and [the identity runbook](runbooks/keycloak.md). Use the callback
`http://localhost:4200/bff/callback` and allow the frontend origin. Staff users need
explicit role assignments; startup does not provide a universal admin login.

## Frontend

In another terminal at the root:

```sh
make ui-install
make ui-dev
```

Open http://localhost:4200. The development server starts the shell and all seven
remotes. Its proxy forwards BFF/Commerce requests to port 8081 and Experience
requests to port 8082. Refresh the browser if a remote starts after the shell.

| Component | Local address |
| --- | --- |
| UI | http://localhost:4200 |
| Commerce/BFF | http://localhost:8081 |
| Experience | http://localhost:8082 |
| Integration diagnostics | http://localhost:8083 (loopback only) |
| Keycloak | http://localhost:8180 |
| Mailpit | http://localhost:8025 |

Java readiness endpoints are `/actuator/health/readiness`.

## Tests and shutdown

```sh
make build
make ui-test
make command-surface-check
make security-source-check
make local-down
```

Feature-specific checks in the Makefile may start isolated Docker test resources.
Logs and generated evidence belong under `.build/`. `local-down` preserves volumes.

## Common problems

- Missing setting: update `.env` from `.env.example` without overwriting existing
  credentials. `make local-config-check` validates local configuration.
- Provider unavailable: local payments use Paystack TEST and delivery uses PandaGo
  stage. Configure real test/stage credentials; missing settings fail closed.
- Migration failure: inspect Docker logs and fix the failed migration. Do not drop
  schemas or clear Liquibase checksums to bypass an error.
- UI cannot sign in: check backend readiness, BFF callback/origin configuration and
  the Keycloak realm/client. See [BFF sessions](runbooks/bff-session-endpoints.md).
