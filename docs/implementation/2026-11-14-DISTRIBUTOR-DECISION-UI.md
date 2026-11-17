# Session report: DISTRIBUTOR-DECISION-UI
Date: 2026-11-14 UTC
Specification/architecture: 1.1.0 / architecture 1.0 plus ADR-028
Status: IN_PROGRESS (implementation complete, local verification passed; isolated database/live acceptance and acceptance commit pending)

## Result

Partner Applications now presents distributor applications and wholesaler/affiliate contact leads as different workflows. Quick approval/rejection shortcuts and the freely editable state dropdown were removed. The modal refreshes the latest authorized detail before enabling an action, shows reviewer ID (or an already-authorized cached staff name) and review note, shows document IDs/scan statuses, and requires an explicit inspection confirmation and staff note for review. Approval requires UNDER_REVIEW, a known different reviewer and configured market; rejection requires a separate confirmation and reason. Terminal and acknowledgement-pending states have no review/approval actions. In-flight requests cannot be submitted twice; failed or uncertain writes require a record refresh before another action. Backend validation remains authoritative.

Raw states, reviewer IDs and the latest version are retained. Delayed queue responses cannot replace a locally known newer version. The open-dialog version is checked before sending If-Match. Acknowledged states are preserved during read failures, and activation pending is not shown as ordinary pending or approved. Lead actions are Start work/Resolve/Close through the existing lead PATCH, with human notes and real lead state labels. Category filtering and phone/location search are retained.

Application request failures are handled inline with actionable messages and actual correlation references. Shared HTTP alerts carry their originating page; the shell filters them by route. Staff Permissions errors remain scoped to Admin Users, and application errors are not duplicated in the staff layout. Other globally reported session failures remain visible.

Experience API-177 now includes nullable decisionReason from the already persisted decision payload. It remains distinct from reviewReason and under the existing Sales authorization. OpenAPI, API_CONTRACT, master clarification and ADR-028 document the additive contract. There is no schema migration, event change or new mutation API.

## Boundaries and limitations

Current file policy only allows the applicant to download DISTRIBUTOR_PROOF. No staff download permission was invented or granted. The page explains that staff cannot open document contents there and must inspect the original documents before confirming review; CLEAN scan status is not presented as proof of a human inspection. The API exposes only the latest review/decision, so the page does not fabricate a full timeline or review timestamps.

No live distributor decisions, staff grants, provider calls or business-data edits were made. The existing independent reviewer/approver rule, document ownership/classification/scan checks, idempotency and post-commit Commerce entitlement acknowledgement remain in force. Runtime restarts and real two-account acceptance are pending.

## Files

Backend: services/experience/src/main/java/com/nigson/experience/distributor/DistributorService.java; DistributorValues.java; services/experience/src/test/java/com/nigson/experience/distributor/DistributorDetailTest.java; contracts/openapi/experience.yaml; docs/API_CONTRACT.md; docs/adr/ADR-028-distributor-decision-detail.md; BACKEND_IMPLEMENTATION_MASTER.md; IMPLEMENTATION_STATUS.md; this report.

Frontend sibling: projects/admin-shop/src/app/sales/applications.component.ts and .spec.ts; projects/shared-ui/src/lib/admin/admin.service.ts; distributor-workflow.ts and .spec.ts; application-actions.spec.ts; projects/shared-ui/src/lib/api-errors.service.ts and .spec.ts; projects/admin-core/src/app/layout/admin-layout.component.ts; projects/shell/src/app/app.ts; vitest.admin.config.mjs; frontend workflow notes.

## Verification

- Backend command: Oracle JDK 25.0.2 / Maven 3.10.0, -o -B -Dmaven.repo.local=C:/Techxplorers/nigson-proj/nigson/.build/m2 -pl services/experience -am test -Dtest=DistributorDetailTest,DistributorPersistenceTest -Dsurefire.failIfNoSpecifiedTests=false. PASS compile and test-compile; 2 detail tests passed (decision/review reason separation, null before decision, unauthorized denial); 3 persistence tests skipped due missing NIGSON_TEST_JDBC_URL. Evidence: services/experience/target/surefire-reports/.
- Frontend: node node_modules/vitest/vitest.mjs run --config vitest.admin.config.mjs. 62 tests passed across 9 files, including the final filter-preservation regression test.
- Production builds: admin-shop, admin-core and shell passed. Final admin-shop/shell rebuild after restoring category filtering also passed. Existing 16 skipped CSS selector warnings remain.
- TypeScript shell --noEmit check passed before final presentation refinements; production builds also compile templates.
- Experience OpenAPI JSON parse and both repository whitespace checks passed.

Python repository/contract/migration checks and genuine PostgreSQL/HTTP/browser acceptance were NOT_RUN because there is no isolated database/Python/browser-role fixture for this session. No integration skip is reported as passing acceptance. This change remains uncommitted; no acceptance commit exists. Existing baseline backend commit: 88f12eb90e64f2daf8388c84433692a69811d8e7.
Next dependency-ready task: restart updated Experience/frontend and qualify real document review, independent approval, rejection reason persistence and pending entitlement acknowledgement using isolated data and two legitimate Sales-authorized accounts. Staff document viewing requires a separately reviewed access-policy change if desired.
