# Guided partner decisions

Distributor applications use explicit review, independent approval and rejection actions. Pending activation/suspension and terminal states remain distinct and cannot be edited as arbitrary statuses. Wholesaler/affiliate records use their lead workflow with Resolve/Close labels; resolving a lead is not distributor approval. Every action requires a staff reason, and rejection has a separate confirmation.

The modal reloads the latest record, preserves the reviewer and server version, checks the independent approver, and stops repeated in-flight requests. Errors remain on the relevant page/action; failed or uncertain writes require refresh before another attempt. The saved decision reason is available after the updated Experience API-177 is deployed. Backend role, document and approval rules are unchanged.

Important limits: staff cannot download distributor proof through the existing owner-only file API. The UI shows this limitation and never treats a CLEAN scan as a completed human review. Only the latest review/decision is available, not a full audit timeline. Live two-account/browser and isolated database qualification remain pending.

Backend evidence: ../../nigson/docs/implementation/2026-11-14-DISTRIBUTOR-DECISION-UI.md. Final local verification results are recorded there.
