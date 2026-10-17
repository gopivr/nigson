# Implementation Session Reports

Specification1.1.0. The supplied blueprint revision had no implementation sessions. Current sessions: 2026-10-05-FOUND-001.md (DONE), 2026-10-05-FOUND-002.md (DONE), 2026-10-05-FOUND-003.md (DONE; acceptance implementation 9c5e57ee7237aaed360a5c18e06ad3b64074e837), 2026-10-16-MSG-001.md (API-190 accepted offline; API-192–194 IN_PROGRESS; live Twilio qualification NOT_RUN). Reports record scoped executed checks; they do not imply product release qualification.

1. Read AGENTS.md, this master, IMPLEMENTATION_STATUS.md, relevant supporting contract and active ADRs before edits. Inspect the existing repository and git state; preserve unrelated user work.
2. Select the next dependency-ready task. Read its referenced tables/APIs/events and section17 transaction rules; record IN_PROGRESS with current task and planned verification. Activation blockers do not prevent unrelated implementation/test work.
3. Never regenerate or overwrite completed code wholesale. Implement the smallest coherent increment, using the existing styles/module boundaries. No core TODO, fake success response or in-memory substitute in production profiles.
4. Source architecture and explicit approved decisions control scope. A prompt mentioning Kafka, Kubernetes, Helm or an API gateway does not authorize replacing SNS/SQS/ECS/BFF. Record proposed contract/schema/boundary changes in an ADR and update traceability/tests before adoption.
5. Run targeted tests and relevant build/format/migration/security checks. On failure retain FAIL and useful safe diagnostics; fix within scope before proceeding. Never mark NOT_RUN/BLOCKED as PASS or reinterpret a failed business invariant as flaky.
6. Validate API/event/schema backward compatibility. Include repository, provider, permissions and negative paths; do not stop at controller unit tests. Never access another owner's tables to shortcut a missing API.
7. Update status after every session, including failed/partial sessions: phase/task IDs, changed files, actual commands/results, commit if created, blockers, next task. Use section48 report. A commit hash is recorded only after git confirms it; document-only deliverables are not runtime implementations.
8. Do not expose secrets/PII in reports or fixtures. Implement test adapters under test profile, never production defaults. Obtain real provider/market/rule approvals through the project's agreed governance before activation.
9. Continue authorized reversible implementation independently. Ask for missing business facts only when the next dependent decision cannot be resolved from the contract/approved config; do not stall unrelated dependency-ready tasks. Publishing/production changes follow the project's existing release authority, not an invented blueprint approval requirement.
10. Stop the current dependent rollout on integrity/security failure, unexplained schema drift or UNKNOWN financial outcome. Preserve durable records, investigate/reconcile and report the precise blocker. Never delete financial evidence to make tests or deployment pass.


User clarification in ADR-008 takes precedence over original assumptions002/003/004/012/013. Implement the required stateful mock servers during MOCK-001; do not merely return hardcoded success from production adapters. Credential-free offline work is authorized and must proceed; adding actual provider accounts/credentials on release uses the versioned configuration checklist.

After each implementation session, write `docs/implementation/YYYY-MM-DD-TASK-ID.md` and update the status file:

```markdown
# Session report: TASK-ID
Date/time: UTC
Specification/architecture version:
Task status: IN_PROGRESS | DONE | BLOCKED | FAILED
Current engineering/product phase:
Summary: concrete resulting behavior
Files changed: exact repository paths
Contracts/migrations/events affected: stable IDs
Acceptance criteria: AC-TASK-ID and actual evidence
Commands executed: exact safe commands, exit result and evidence paths
Build: PASS | FAIL | NOT_RUN (reason)
Tests: PASS | FAIL | NOT_RUN | BLOCKED (reason)
Security/migration/compatibility checks: results
Last successful commit: actual hash or NONE
Open issues/activation inputs: IDs and owner
Next dependency-ready task: ID and reason
```

Do not include raw credentials, customer data or fabricated successful command output. Passing a formatting or document check is not a backend build/test pass.
