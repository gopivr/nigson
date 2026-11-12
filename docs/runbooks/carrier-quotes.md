# PandaGo delivery quotes

Commerce freezes the cart/address/package context and calls private API-182 using workload identity and a signed actor. Integration uses the registered PandaGo outlet and Google geocoding, then stores the acknowledged quote. Provider calls run outside database locks.

Local uses official stage endpoints with TEST credentials; production requires LIVE. Configure `NIGSON_PANDAGO_ENABLED`, the registered key file, client/key/vendor IDs, account fingerprint and unexpired approvals. Google address lookup requires Geocoding API access and billing.

Automatic shipment booking/tracking/cancellation orchestration is unavailable. Unsupported booking requests fail before creating a shipment or job. Existing order records and approved manual fulfillment remain intact.
