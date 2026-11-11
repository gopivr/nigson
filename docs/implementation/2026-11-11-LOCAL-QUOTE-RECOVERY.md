# Local quote restart recovery

Root causes: LocalStack lost the configured actor signing key, blocking Commerce delegation before Experience was called. Quote submission also lacked the BP3 quote feature and used the Commerce identity/public localhost token endpoint for Experience SKU validation.

Restored emulator-only signing trust, scoped recovery to the correct local Compose project, and made run-core recover it before app startup. Existing valid keys remain unchanged; transport errors never trigger rotation. Enabled local quotes in .env, corrected the Experience client/secret and used the configured Keycloak backchannel with bounded token reads. Database/session data and checkout/payment gating preserved.

Verification: 28 local contract tests and two HTTP adapter tests pass. Docker Experience build/full Maven install passed and local services were refreshed. Authenticated BFF quote-request GET succeeds; actual synthetic test-account POST returned NEW, and the created lead appeared in own history. Evidence: .build/quote-restart-java-tests.log, .build/quote-restart-docker.log, .build/quote-request-submit-smoke.log. Sandbox HTTP-bind failure was rerun successfully outside sandbox. No delivery booking/payment or production changes. Synthetic local validation lead retained. No commit created.

Normal startup remains make local-up followed by make run-core; no extra quote-provider service is required.
