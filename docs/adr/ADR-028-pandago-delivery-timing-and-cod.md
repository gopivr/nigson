# ADR-028: PandaGo delivery timing and cash on delivery

Status: Accepted user direction, 17 November 2026; offline implementation under verification.

## Decision

Delivery timing and payment are independent choices. The storefront offers immediate or scheduled delivery and hosted prepayment or cash on delivery. Commerce defaults omitted timing to IMMEDIATE for backward compatibility. Scheduled pickup uses an ISO UTC instant internally, validated between 45 minutes and seven days from now; the PandaGo adapter writes Unix seconds to `preordered_for`. Immediate requests explicitly write `preordered_for: null`.

Hosted checkout retains the existing `HOSTED` application value and verified Paystack settlement requirement; its carrier value is `PAID`. COD uses `CASH_ON_DELIVERY`, creates no online payment attempt, and keeps financial state UNPAID. Checkout allocates reserved stock and converts the cart atomically, placing the order in PROCESSING. Emit the existing order.processing event rather than enqueuing payment reconciliation through order.pending. Retain authoritative stock, idempotency, audit and current ownership checks. Never represent COD allocation as received payment or invent a cash settlement transaction.

Freeze timing/payment in the order carrier snapshot and expose their carrier values in the private shipment context. Revalidate scheduled timing at checkout. Public delivery quote and checkout contracts document the added choices and nullable COD payment response; no migration is necessary because these options reside in existing JSON snapshots.

## Existing execution limit

Automatic carrier booking/reconciliation remains unavailable in this repository. No production caller invokes PandagoAdapter.confirm; Commerce booking-result/dispatch still assumes a shipping label. These changes implement choices, snapshots and adapter payloads, not a new automatic shipment worker, cash remittance reconciliation, or provider qualification. Restoring that workflow requires durable booking/UNKNOWN reconciliation and PandaGo no-label lifecycle support with separate acceptance evidence. No provider operation or deployment was performed in this session.
