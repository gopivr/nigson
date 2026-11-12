# Local shutdown repair
Status: DONE. Last repository commit: `b08cf99`; changes uncommitted.

Cause: unresolved `.env` merge markers prevented Compose validation before shutdown. Shutdown now uses only local Docker project labels, covers all profiles and preserves named volumes. Other commands identify conflict lines without logging values; conflicting credentials are not selected automatically.

Validation: local shutdown succeeded; no project containers remain and all named volumes are unchanged. Local configuration tests and the 117-test contract suite pass. Evidence: `.build/provider-removal/local-down-verification.log`, `.build/provider-removal/contracts.log`. Stack remains stopped as requested.
