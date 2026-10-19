# COM-FULFI-002 acceptance evidence

Captured 2026-10-18 UTC from the final local harness runs. Full raw logs remain in the ignored `.build/fulfillment002*` directories; no customer or provider credentials are included.

## Focused fulfillment harness

`.build/fulfillment002-native-final.log`

- Commerce: 23 tests, 0 failures, 0 errors, 0 skipped.
- Experience build compatibility: 2 tests, 0 failures, 0 errors, 0 skipped.
- Integration build compatibility: 2 tests, 0 failures, 0 errors, 0 skipped.
- The Commerce `SecurityAssemblyTest` passed actual BFF HTTP, API-160 disabled/no-write checks and API-051 route security. Delivery rules and shipment persistence ran against isolated PostgreSQL.
- Result: Maven `BUILD SUCCESS`; runner local-state preservation assertion passed.

## Carrier/source acceptance

`.build/fulfillment002-carrier-native.log`

- Commerce selected suite: 70 tests, 0 failures, 0 errors, 0 skipped.
- Integration selected suite: 52 tests, 0 failures, 0 errors, 0 skipped.
- Total: 122 tests, 0 failures, 0 errors, 0 skipped; Maven `BUILD SUCCESS`.
- The real HTTP security assembly exercised API-119/120 with the local Keycloak workload/JWKS and TLS/SigV4 storage fixture; wrong scopes were rejected, private label HEAD/replay was verified, and UNKNOWN/out-of-order carrier states did not dispatch stock or regress accepted state.
- Shipment tracking HTTP verified ownership, safe projection and `Cache-Control: no-store`.
- Runner local-state preservation assertion passed.

## Contract, schema and repository checks

- `python3 -m unittest discover -s tests/contract` — 84 tests passed.
- `python3 scripts/contracts/authorization.py --check` — generated commerce OpenAPI matches the reviewed generator, including API-160 disabled operation.
- `python3 scripts/database/generate.py --check` — generated schema/migration artifacts match the normative catalog.
- `python3 scripts/check_repository.py --tracked` — PASS.
- `git diff --check` — PASS.

No DDL was introduced; the native harness applied owner migration sets fresh and repeated them. No live provider was contacted.
