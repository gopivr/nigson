# Session report: LOCAL-DEMO-USER-001

Date: 1 November 2026 (America/New_York)
Specification/architecture: 1.1.0 / 1.0 plus ADR-008
Task status: DONE locally
Phase: local API validation support

Created `demo.validator@example.invalid` in the existing local Keycloak realm with the configured local fixture password. Rebound the synthetic seeded Commerce party to its real Keycloak subject and issuer, retained its deterministic ID/customer relationships, granted the seven declared application staff roles, and verified the resulting BFF session contains CUSTOMER plus those seven roles. Added a provisioning audit record. Synthetic Experience lead/destination/consent external party IDs were linked in an owner-local transaction. No unrelated users, customer records, identity passwords or production settings were changed.

The account has no fabricated MFA assertion. Sensitive actions still require recent real MFA; private Experience/Integration APIs require workload/signed actor authentication and are not all browser-accessible. Existing frontend owner relay limitations and absent later releases are documented. This is not a universal authorization bypass.

Initial real HTTP acceptance verified profile and draft catalogue access. It exposed three errors: an empty seeded order address snapshot failed strict response mapping; BP1 customer addresses and orders queried organization tables that only exist from BP3. Added the complete synthetic address snapshot and repaired only the old empty synthetic snapshot. BP1 repository queries now avoid later-release tables; phase3+ organization ownership logic is retained. OrderConfiguration supplies the configured product phase. A native PostgreSQL regression temporarily renames the three organization tables in the isolated test database, proving the BP1 reads work without them, and verifies another party sees no addresses and explicit organization filtering is rejected.

Files changed:

- `scripts/local/provision_demo_user.py`
- `scripts/local/seed_demo.py`
- `services/commerce/src/main/java/com/nigson/commerce/party/JdbcPartyRepository.java`
- `services/commerce/src/main/java/com/nigson/commerce/order/JdbcOrderRepository.java`
- `services/commerce/src/main/java/com/nigson/commerce/order/OrderConfiguration.java`
- `services/commerce/src/test/java/com/nigson/acceptance/party/Bp1ReadPersistenceTest.java`
- `docs/DEMO_VALIDATOR_GUIDE.md`
- `docs/LOCAL_APPLICATION_GUIDE.md`
- `IMPLEMENTATION_STATUS.md`
- This report

Contracts/migrations/events affected: no contract/DDL/event change; BP1 address/order read compatibility restored.
Acceptance: dedicated login owns seeded customer records, reports all application roles and can read seeded profile/addresses/draft products/orders through their implemented APIs.

Commands/evidence:

- `python3 -m py_compile scripts/local/provision_demo_user.py scripts/local/seed_demo.py`: PASS.
- `python3 scripts/local/provision_demo_user.py`: PASS and repeated PASS, exact seven active grants verified. Effective app-profile Compose configuration is used; project/environment/Unix socket guards and explicit known-identity ID checks prevent unrelated takeover. Passwords/tokens are not printed.
- Provisioning initial attempts: first refused missing app-profile config before mutation; second created the fixture identity but rejected the missing unmanaged custom attribute (Keycloak omitted it). Recorded that just-created identity's verified ID in ignored `.build/demo-validator-identity.json`; provisioning now uses that ID for safe repeat runs. No password reset or duplicate user was performed.
- Local administration probes: old master bootstrap login refused; existing registration service account works for user management. Its realm role/flow/client management is correctly forbidden. No admin recovery or account elevation in Keycloak was attempted.
- `python3 -m unittest discover -s tests/contract -p test_demo_seed.py -q`: PASS, 5 tests.
- Initial isolated native run (`.build/demo-validator-native.log`): 27 selected, 8 passed, 19 order tests skipped because their stateful provider mock was absent. BP1 missing-table and existing party tests executed. Follow-up results with the standard order fixture are recorded below.
- `docker compose --profile app build commerce > .build/demo-validator-commerce-build.log 2>&1`: PASS.
- `docker compose --profile app up -d --no-deps --wait commerce`: PASS, healthy.
- `python3 /private/tmp/nigson-demo-user-probe.py` after installation: PASS, exact party and all eight roles plus HTTP 200 with expected seeded data for profile, addresses, staff products, customer orders and staff orders; validation session logged out.
- Stateful regression run (`.build/demo-validator-order-native.log`): all 27 tests passed with zero failures/errors/skips, but the final preservation assertion failed because the intentionally authorized local Commerce restart occurred during the harness snapshot interval. That is a harness-level FAIL, not claimed as overall PASS. Final rerun without local service changes passed (below).
- `python3 /private/tmp/nigson-demo-user-probe.py`: real Keycloak/BFF fixture login and exact party/roles passed; before BP1 fixes, profile, staff products and staff orders returned 200 with expected fixtures; customer addresses/orders returned 500. Verification sessions logged out successfully. Cookies/credentials/tokens remain in process memory and are never printed.
- Final stateful native run (`.build/demo-validator-final-native.log`): PASS, 27 tests, zero failures/errors/skips; final local-state preservation assertion passed and isolated containers were cleaned up. Exact command:

```sh
python3 -c "import runpy; from pathlib import Path; m=runpy.run_path('scripts/order/verify.py'); m['main'](java_tests='Bp1ReadPersistenceTest,PartyPersistenceTest,OrderPersistenceTest', java_projects='services/commerce', evidence=Path('.build/demo-validator/final-database-evidence'), run_python=False, java_environment=m['fixture'], completion='PASS: BP1 absent-table reads, party isolation and stateful order regressions; local state preserved')" > .build/demo-validator-final-native.log 2>&1
```

- `git diff --check`: PASS.

Build: PASS, local image installed and Commerce healthy.
Security/migration/compatibility: local-only admin fixture tooling; owner-local data; no MFA/CSRF/ownership bypass, no migrations or resets. Unrelated edits preserved.
Last successful commit: NONE created.
Open issues: recent real MFA and private service authentication remain required; no live provider/commercial activation. Browser click automation not run.
Next task: INFRA-001 continues independently.
