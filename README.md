# Nigson

One repository for the Java backend, Angular microfrontends and AWS infrastructure.

| Path | Purpose |
| --- | --- |
| `services/` | Commerce/BFF, Experience, Integration and optional AI |
| `shared/` | Shared protocol and security libraries |
| `frontend/` | Angular shell and seven microfrontends |
| `infrastructure/terraform/` | Infrastructure definitions |
| `scripts/` | Development, testing and deployment tools |
| `contracts/` | API, event and security contracts |

## Start here

- [Run locally](docs/LOCAL_APPLICATION_GUIDE.md)
- [Deploy to AWS](docs/DEPLOYMENT.md)
- [Technical reference](docs/README.md)

## Common commands

```sh
# Java 25, Docker Compose and Python 3 required
cp .env.example .env  # first checkout only; keep existing configuration
make local-config-check
make local-up
make run-core

# Node 24.15.0; run in a second terminal
make ui-install
make ui-dev
```

Open http://localhost:4200. Authentication and provider-backed features need their
configuration; there are no built-in production admin credentials.

```sh
make build                 # Java build and tests
make ui-test               # UI regressions and deployment packaging checks
make security-source-check # Check tracked source for credential patterns
make local-down            # Stop containers; retain database volumes
```

The root [Makefile](Makefile) lists feature-specific checks. Test output belongs
under ignored `.build/`, not in committed status reports. Keep credentials in
local ignored environment files or deployment secrets, never in source.
