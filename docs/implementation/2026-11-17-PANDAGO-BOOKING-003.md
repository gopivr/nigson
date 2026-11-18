# Session report: PANDAGO-BOOKING-003

Date: 2026-11-17 America/New_York.
Specification/architecture: 1.1.0, ADR-010 restore controls and ADR-029 booking workflow.
Phase: EP05 / BP1 checkout and fulfillment.
Status: implementation verified offline; user-approved local staging booking activated and core health verified.

## Result

Commerce automatically creates a durable complete shipment intent and EVT-027 for newly eligible orders. Prepaid requires independently verified successful Paystack settlement and allocated stock; an unverified paid flag or browser redirect cannot trigger booking. Confirmed COD skips Paystack, remains UNPAID and uses allocated stock. No preparation delay or dependency on COURIER_ACCEPTED_DELIVERY. Genuinely scheduled delivery retains its chosen time.

Integration consumes EVT-027, reads frozen Commerce context through API-119, re-quotes at the frozen delivery fee, commits its prepare checkpoint before confirmation HTTP, and saves the actual PandaGo order ID before acknowledging through API-120. Leases, row versions, stable booking references and generation/cutoff fence creation. Duplicate events and acknowledgement retries never submit another booking. Expired prepared leases and ambiguous responses become UNKNOWN. Response loss with no returned provider ID requires operator/provider reference reconciliation, rather than an invented lookup or blind retry. A known provider ID can be read without recreation.

BOOKED accepts the real PandaGo reference without PDF storage or a label requirement. Booking does not debit stock or pretend a physical handover occurred. Authenticated account order tracking displays the returned PandaGo order ID. Current callback, cancellation and staff reconciliation enablement remains separately scoped.

Nullable additive Integration migration026 preserves historical rows and applied migration checksums. It records booking_reference, creation_generation, source_occurred_at, prepared_at, account_hash and minimal result_body. No customer addresses, phone numbers or credentials are persisted in these checkpoints. Owner boundaries and existing API-119/120/EVT-027 identities remain intact. OpenAPI, normative section10 schema and generated manifest match implementation.

## Verification and evidence

- PASS: `python3 scripts/carrier/verify_pandago_booking.py` — 70 Java tests, no failures/errors/skips: Commerce33 (order persistence25, result validation/state4, order values2, build2), Experience2, Integration35 (provider22, booking persistence9, private HTTP2, build2). Evidence `.build/pandago-booking003/acceptance.log`; fresh/repeat migrations under `.build/pandago-booking003/database-evidence/`. Harness preserves existing local containers, volumes and users.
- PASS: duplicate intake, prepaid verification gate, COD without Paystack, once-only shipment intents, no-label result/reference persistence, acknowledgement replay, foreign workload denial, no stock debit on booking, lease concurrency, prepared crash/timeout, generation mismatch, changed fee rejection, pre-prepare read outage, no provider HTTP inside SQL transactions.
- PASS: `python3 -m unittest discover -s tests/contract -p 'test_*contract.py'` — 64 tests; `.build/pandago-booking003/contracts.log`.
- PASS: `python3 -m unittest discover -s tests/contract -p 'test_local_configuration.py'` — 40 tests; `.build/pandago-booking003/local-config.log`.
- PASS: `python3 scripts/database/generate.py --check`, `python3 scripts/contracts/authorization.py --check`, both repositories `git diff --check`.
- PASS: `npx vitest run --config vitest.checkout.config.mjs` in frontend — 26 tests/seven files; `.build/pandago-booking003/ui-tests.log`.
- PASS: `NG_BUILD_MAX_WORKERS=2 npx ng build account --configuration development` — `.build/pandago-booking003/account-build.log`.
- Local activation: first `make run-core` failed because the previously disabled Integration worker had no container workload-secret/Commerce-base-url wiring. Added these environment bindings and configured/verified the local Integration Keycloak client using canonical scopes. The second run revealed generic provider jobs also enabled an unconfigured career-sheet handler. Booking now constructs its own exact WorkloadIdentity client and does not enable the generic jobs switch. The third run exited0; Commerce, Experience and Integration all reported healthy. Final evidence `.build/pandago-booking003/run-core-enabled.log`.

## Failure history and activation authority

Initial helper import ordering caused a harness circular import; fixed. Initial compile lacked AuditValues import; fixed. Expanded acceptance exposed runtime-role deletion of append-only shipment_event during test cleanup; switched isolated cleanup to the fixture administrator, then all70 passed. The interruption/resume lost /tmp logs; resumed verification writes durable ignored .build evidence. Earlier log/lease status preservation and obsolete label dependencies were corrected before the final run. Initial enabled startup then exposed missing local Integration workload identity wiring; corrected in Compose/bootstrap and covered by an extra preflight test. The generic provider-jobs coupling then activated the unrelated career-sheet handler; removed that coupling, added a standalone booking-client configuration test, and retained canonical Integration identity provisioning.

Automatic approval review initially rejected persistent local staging activation because it could create deliveries/costs beyond implementation-only authorization. The user then explicitly answered Enable staging booking. Only after that approval was .env updated with the opt-in flag, a new generation UUID, and a UTC new-order cutoff. Existing orders are excluded; no historical order backfill or fabricated audit orders. No production activation or message to PandaGo was performed. A default-disabled configuration template is added to .env.example.

## Source evidence and remaining limits

Backend implementation source is in user-created commits through 93e55a3f533398cdbc8f45f8ccca8bab61c21a70; frontend source through f5d2242848fc1939bf2337d1a60af24f4bdd62a8. No assistant-created commit. The resumed additions include default-disabled .env.example documentation; local Integration worker URL/secret bindings, canonical Keycloak Integration client setup and preflight checks/tests; independent PandaGo workload-client configuration and its test; this report/status. .env is local activation configuration and contains the generated local workload credential; it must never be logged.

No new staging order was fabricated for qualification; tests use isolated synthetic HTTP providers only. PandaGo quality-audit approval and production readiness are not inferred from offline tests. Genuine stage booking evidence will come from new eligible storefront orders after the user-approved activation. Next: use approved stage checkout data to create and inspect orders; retain UNKNOWN for manual reference reconciliation if PandaGo accepted a request without returning its ID.

## Changed areas

Commerce AutomaticShipmentWorker, CarrierShipmentContext/JdbcShipmentRepository, no-label CarrierResultService/Values/Configuration, fulfillment configuration/DTOs; Integration PandagoBookingRequested/Worker/CommerceClient/Configuration, migration026 and BP1 include; schema/master/OpenAPI generators/contracts; source/native HTTP/PostgreSQL tests and scripts/carrier/verify_pandago_booking.py; frontend order service/tracking view and tests; docker-compose.pandago.yml, local configuration preflight/tests, .env.example, local .env, ADR-029 and implementation status/report. Resumed timing/COD edits are documented in PANDAGO-DELIVERY-002.

## Final activation evidence

`make run-core` exited0 with all three core application containers healthy. The local Integration client was separately verified with its canonical scope/subject mapper; evidence `.build/pandago-booking003/integration-identity.log`. The bootstrap now reads the same merged PandaGo Compose configuration as runtime, preventing false omission of Integration from client provisioning. No stage orders were created by this verification.

Follow-up isolated configuration/provider/private HTTP tests passed28, and the final independent booking-client configuration test suite passed5; `.build/pandago-booking003/final-wiring-tests.log` and `booking-config-tests.log`. Local preflight tests40 and contract tests64 passed after runtime wiring fixes. Existing initial activation failures are retained in run-core.log and run-core-verified.log. Raw diagnostic exceptions stay in ignored integration-startup-private.log.

Source changes after the user's carrier commits additionally include docker-compose.yml URL/credential bindings, carrier-only activation in docker-compose.pandago.yml, scripts/local/enable_bp3.py merged-config/Integration canonical identity provisioning, scripts/local/dependencies.py worker credential preflight and accurate bootstrap message, .env.example defaults, PandagoConfiguration independent credential construction, configuration/local tests, and local approved .env values. No new commit was made during the resumed work.
