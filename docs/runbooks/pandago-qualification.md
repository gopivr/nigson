# PandaGo stage qualification: notes and pickup code

Provider feedback received 25 November 2026 requires recipient notes to contain only floor/door details and additional delivery instructions, and pickup_tasks.pickup_code to be non-null, alphanumeric and under32 characters.

## Application behavior

Checkout has an optional Floor, door and delivery instructions field (existing address line2, maximum200 characters). Saved address instructions are shown at checkout and can be edited in Account → Addresses. New checkout addresses preserve these details through the frozen order address. Example: `2nd Floor, Door B – leave at the door`. Put the street address in Street address. Do not put order references, names, phone numbers, item lists or payment details into the instructions field. Free text remains user supplied; the app does not infer or fabricate apartment details.

The booking worker forwards only frozen line2 into recipient.notes. Empty notes are omitted. Order references are not injected into sender.notes or recipient.notes. The backend already enforced this mapping at the start of this follow-up.

The backend derives the uppercase last10 alphanumeric characters of the immutable order number and sends it as pickup_tasks.pickup_code. Admin → Sales → Orders shows the same code in the list and order detail. For example, order number NIG_0123456789ABCDEF0123456789ABCDEF produces 6789ABCDEF. This is an illustrative code, not a submitted order. Missing/invalid codes fail closed before confirmation.

## New qualification booking

1. Deploy/restart the reviewed backend and frontend versions in the environment configured for the real PandaGo stage account. Verify the private booking worker, its creation generation and time-bounded approval are enabled for stage. Keep real account credentials private.
2. With an approved stage customer, product/stock and serviceable delivery address, create a NEW Nigson order. Enter the floor/door/instructions example above. A saved address must already contain the approved instructions, or use a new address.
3. Use the configured supported payment flow. Complete any necessary prepaid TEST payment; use COD only when approved for that market/account. Do not send live payments or invent stock/prices for qualification. Let the durable application booking path create the PandaGo order.
4. In Admin Orders, note the Nigson order number and displayed pickup code. Confirm the carrier booking is BOOKED and obtain the real PandaGo order ID from the authorized shipment/provider record. A Nigson UUID, mock fixture ID or client_order_id is not the PandaGo order ID.
5. Inspect the stage provider order using authorized tooling and confirm recipient.notes and pickup_tasks.pickup_code match. If the outcome is UNKNOWN, reconcile by the existing booking reference; do not blindly resubmit or reuse an old failed booking. Existing orders are not retroactively updated.
6. Share the new PandaGo order ID(s) and their matching pickup codes with PandaGo through your usual contact. Confirm which submitted example includes floor/door notes. Do not attach credentials or unnecessary personal data.

No new stage booking or order ID has been created by this implementation session. Approved test-order inputs and actual deployed execution remain required.

## Offline regression

`python3 scripts/carrier/verify_pandago_qualification.py` creates isolated local PostgreSQL/LocalStack containers and runs pickup derivation, booking persistence, HTTP adapter and private context tests. It never calls PandaGo stage/live. Frontend focused tests cover preserving instructions, empty/overlong notes, saved-address display, quote invalidation and operator code parity. Offline fixture IDs are not qualification submissions.
