# Runtime Mock Removal

Date: 2026-11-11  
Status: IMPLEMENTED — runtime mock routing and local demo fixture generation are removed from application profiles; live-provider qualification remains blocked on approved provider accounts and credentials.

## Result

- Provider HTTP `TEST` mode cannot send requests; `MOCK` is limited to loopback simulators in `APP_ENV=test`; all other provider traffic requires `LIVE`.
- External SMTP sandbox mode is rejected. The explicit PandaGo quote overlay and qualification utility now require official LIVE endpoints; the overlay remains disabled by default.
- Twilio verification intake now rejects mock and sandbox modes outside tests instead of persisting non-live provider intent.
- Compose defaults Paystack, FedEx, Twilio, and email to official provider endpoints in `LIVE` mode. Business-provider WireMock services were removed; application email is no longer routed through Mailpit.
- Local mock adapters for files, distributor entitlements, anti-spam, Google Sheets, and AI are restricted to tests. Google Sheets may use its official API; AI defaults to Bedrock outside tests.
- Removed local provider-mock Compose startup and manual reset/scenario/journal controls; provider simulator management is confined to the automated Testcontainers harness.
- LocalStack and Mailpit remain infrastructure utilities, not business-provider fallbacks.
- The local synthetic seed and demo-account generators have been retired in the follow-up below. Test-only identities remain in the test fixture manifest and are created only in isolated cold-start projects. Existing database/identity volumes were not deleted. The ignored `.env` modification present in the worktree was left untouched.
- Public enquiry anti-spam now requires a real Google reCAPTCHA v3 token; the browser no longer creates a synthetic token. Lead intake can use the database-backed distributed limiter with trusted-proxy-aware source resolution.
- Authenticated distributor applicants can upload real PDF/JPEG/PNG registration proof through the Experience signed-upload API. Submission is gated until the owner-authorized download endpoint confirms the file is clean; the UI exposes upload, scan-status, and download states instead of a sample document ID.

## Validation

- `JAVA_HOME=/Library/Java/JavaVirtualMachines/jdk-25.jdk/Contents/Home ./mvnw -B -q -pl services/commerce,services/integration,services/experience -am test` — PASS, including the provider HTTP, SMTP mode, and Twilio intake guard tests (1 each; zero failures/errors/skips).
- `python3 tests/providers/paystack/test_readiness.py` — PASS (8 tests; zero failures).
- `python3 -m unittest discover -s tests/contract -v` — PASS (115 tests).
- `python3 -m compileall -q scripts/local/seed_electronics.py scripts/local/dependencies.py scripts/local/verify_pandago.py scripts/platform/provider_readiness.py tests/providers/paystack/test_readiness.py services/ai/src/nigson_ai` — PASS.
- Base Compose and PandaGo overlay `config --quiet` checks using `.env.example` and offline placeholders — PASS.
- `git diff --check` — PASS.
- `npm run test:distributor` — PASS (13 tests, including the upload/completion/clean-status flow).
- `npm run ng -- build marketing` — PASS; pre-existing Sass selector warnings remain.
- Experience Maven tests (`./mvnw -B -q -pl services/experience -am test`) — PASS after correcting the validation-code assertion.
- Full AI pytest suite was not run because pytest/uv are unavailable.

## Qualification limits

No live provider requests were made. Live Paystack, FedEx, Twilio, SMTP, Google, and Bedrock behavior is not qualified; required account approvals, credentials, sender/template configuration, and provider-specific operational approvals are not present. Automated tests use isolated simulators; they must not call provider sandbox/live endpoints.
Google reCAPTCHA verification and production lead intake remain disabled until the public site key, secret key, allowed hostnames, recipient group, database limiter key/mode, and trusted proxy CIDRs are configured.

## Follow-up: local demo fixture retirement

- Removed local domain-data seed scripts, demo-account provisioning/grant scripts, and their obsolete guide/test.
- The imported Keycloak realm now contains no example users. The test identity manifest is consumed only by the cold-start test, which generates an ephemeral password and requires both `ENV=test` and an isolated `nigson-test-*` Compose project.
- Local startup no longer builds or starts provider simulators. Frontend sample distributor documents, local admin-state fallbacks, and synthetic success behavior were removed. The distributor form now uploads actual proof and waits for server-confirmed scan readiness before submitting an authenticated application.
- Existing local database/identity volumes were intentionally preserved; synthetic rows or users already stored there remain untouched. Existing `.env` was not modified.
