# Provider timeout and UNKNOWN outcome recovery

This runbook applies when a provider may have accepted a write but the application did not durably receive its response. A timeout, connection reset, malformed response or process crash after submission is not evidence of provider rejection.

## Classify before acting

1. Identify the owner workflow and safe correlation/command reference from its protected logs or authorized staff view. Do not copy provider bodies, credentials, account identifiers, addresses, payment references or label keys into a ticket.
2. Determine whether durable intent and request hash committed before the HTTP call. If not provable, keep the operation blocked and escalate; do not infer that no remote side effect occurred.
3. Preserve `UNKNOWN`, its original mode/account identity, idempotency/business key, attempt/lease/checkpoint and audit evidence. Do not change the request body, provider account or transport to make a retry succeed.

## Workflow-specific recovery

- **Payments/refunds:** Commerce owns financial truth. Use the approved Paystack read/reconciliation path with exact amount/currency/business reference. Incomplete or ambiguous provider reads leave the attempt/refund `UNKNOWN`; never create a second payment/refund or manually mark it failed/succeeded. Finance-approved resolution follows the existing policy.
- **FedEx booking:** keep the order paid/held and shipment unshipped. Use the authorized API-191 reconciliation path with the original booking UUID/reference and account fingerprint. Reconciliation reads/recoveries cannot mint fresh create permission. Missing or ambiguous provider evidence remains `UNKNOWN` and visible for operator action.
- **FedEx cancellation:** retain dispatch exclusion while outcome is `UNKNOWN`; reconcile against the original saved physical-piece references. Do not issue a second cancellation based only on a timeout.
- **SMS/WhatsApp/email:** keep the delivery outcome `UNKNOWN` where the provider may have accepted the send. Match a signed status receipt or approved provider query to the existing job/SID; do not send another message because a callback was late.

After resolution, verify the durable owner row, audit transition, outbox/inbox receipt, stock/financial invariants and idempotent replay. Replays must return or continue the same durable command, not create a new side effect. Generic queue retry endpoints do not override provider `UNKNOWN` rules. Live provider qualification remains separate from local mock success.
