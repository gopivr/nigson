# Integration build repair

Fixed the carrier/providers module cycle by moving Pandago quote orchestration configuration and its test into carrier. Kept adapter configuration in providers. Fixed API-119 postal-address conversion for the new nullable coordinate fields; corrected the test fixture to match the actual order-address contract. Strict unknown-field checks remain enabled.

Verification: `./mvnw -B -pl services/integration -am clean test` passed: integration 201 tests, 105 database-gated skips, zero failures/errors. Full `./mvnw -B install` passed across Commerce, Experience, Integration and shared modules. Docker image rebuild not run. Logs: `.build/integration-fix-clean.log`, `.build/integration-fix-install.log`. Initial failures retained in `.build/integration-fix-unrestricted.log` and `.build/integration-fix-verified.log`. No schema or external API changes, deliveries, deployment or commit.
