# PandaGo notes and pickup handover — 25 November 2026

Checkout now captures floor, door and extra delivery instructions as existing address line2 (maximum200 characters). Saved instructions are shown and edited in Account Addresses. Only these instructions flow into rider-visible recipient.notes. Street/contact/order/payment information belongs in its separate existing fields.

Admin Sales Orders shows the backend pickup code: the last10 alphanumeric order-number characters, uppercase. Match this visible code with the rider. The list also matches searches for that code. This is a deterministic display of the existing backend convention, not a new generated identifier or API field.

Verified:26 checkout and9 Admin Orders focused Vitest tests; no-emit Angular compiler checks for shop, admin-shop and account. Backend scope:39 isolated Java tests and8 fulfillment contracts. No real stage order created. Deploy both reviewed repositories, create a new approved stage order, and capture the actual PandaGo order ID. See backend docs/runbooks/pandago-qualification.md and docs/implementation/2026-11-25-PANDAGO-QUALIFICATION-004.md for exact evidence and pending provider acceptance.

## Revalidation after main merge — 25 November 2026

The notes input, immutable backend mapping and operator pickup-code display survived the merge. Admin Orders had retained a call to the removed statusFilter signal and lost product/SKU matching. Removed that obsolete call and combined pickup-code, order, customer, product and SKU search under the incoming progress/attention tabs. Four regression tests construct the real component fields to verify initial rendering, reactive search/tabs and cancellation exclusion for shipment creation/dispatch/delivery.

Post-fix frontend evidence:26 checkout tests and31 cancellation/order-service/customer/admin tests (including13 Admin Orders tests); shop/admin-shop/account no-emit Angular compiler checks pass. Backend report: docs/implementation/2026-11-25-PANDAGO-MERGE-005.md. No stage/live bookings were submitted.
