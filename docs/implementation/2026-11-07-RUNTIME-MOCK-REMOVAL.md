# Session report: RUNTIME-MOCK-REMOVAL
Date/time: 2026-11-07 UTC
Specification/architecture version: 1.1.0 / 1.0 + ADR-008
Task status: IN_PROGRESS
Current engineering/product phase: EP10 / runtime mock removal
Summary: Public market reads now skip browser session-cookie validation, so an absent, expired, or unknown session cookie cannot turn the public market listing into an authentication failure. Browser bearer/actor credentials remain rejected.
Follow-up: Enabled the explicit local security profile with local-only session encryption key material, a LocalStack actor-signing key, a generated Commerce workload secret and the isolated Compose network CIDR. No production credentials were created. Rebuilt/restarted application services; Commerce, Experience and Integration report healthy. The frontend `/bff/login` returned 302 to the local Keycloak realm; the login page returned HTTP 200 with title `Sign in to Nigson`. Credentials and raw key values are intentionally not recorded.
Files changed: `services/commerce/src/main/java/com/nigson/commerce/security/TrustFilter.java`; `services/commerce/src/test/java/com/nigson/acceptance/market/MarketHttpAcceptance.java`; `docker-compose.yml`; `.env` (ignored local configuration); `IMPLEMENTATION_STATUS.md`
Contracts/migrations/events affected: None. Preserves API-029's existing public authentication policy.
Acceptance criteria: API-029 public GET remains accessible with an unknown session ID; verified by `NIGSON_TEST_MARKET` HTTP acceptance.
Commands executed:
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home ./mvnw -B -pl services/commerce -am -DskipTests test` — PASS (compile and test compilation; tests skipped).
  - `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home ENV=test python3 scripts/market/verify.py` — PASS (SecurityAssemblyTest, market persistence and values tests; 25 market tests, zero failures/errors/skips; HTTP acceptance exercised with `NIGSON_TEST_MARKET=true`).
  - `git diff --check` on the changed Java source/test files — PASS.
  - `ENV=local python3 scripts/local/restore_actor_key.py` — PASS (emulator-only actor key configured; key material withheld).
  - `make run-core` — PASS (rebuilt and restarted Commerce, Experience and Integration; all healthy).
  - `make local-config-check` and `docker compose --profile app config --quiet` — PASS.
  - Frontend-to-Keycloak login redirect probe — PASS (302 to the local `nigson-local` realm; branded login page HTTP 200, title `Sign in to Nigson`).
Build: PASS for Java compilation and test compilation; no production image build run.
Tests: PASS for the isolated market verification suite. Other product tests NOT_RUN.
Security/migration/compatibility checks: Confirmed market reads are public as specified by API-029; invalid Authorization remains rejected by the acceptance suite. Local-only `.env` remains mode 600 and git-ignored. No migrations or contracts changed.
Last successful commit: NONE
Open issues/activation inputs: Runtime mock removal remains in progress; this increment does not complete the broader task.
Next dependency-ready task: Continue RUNTIME-MOCK-REMOVAL; no roadmap dependency changed.
