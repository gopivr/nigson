-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE commerce.party ADD CONSTRAINT party_identity_issuer_identity_subject_uq UNIQUE (identity_issuer,identity_subject);

CREATE INDEX party_lower_email_ix ON commerce.party (lower(email));

ALTER TABLE commerce.customer_profile ADD CONSTRAINT customer_profile_party_id_uq UNIQUE (party_id);

CREATE INDEX customer_profile_party_id_fk_ix ON commerce.customer_profile (party_id);

CREATE UNIQUE INDEX staff_role_assignment_party_id_role_uq ON commerce.staff_role_assignment (party_id,role) WHERE revoked_at IS NULL;

CREATE INDEX staff_role_assignment_party_id_fk_ix ON commerce.staff_role_assignment (party_id);

CREATE INDEX staff_role_assignment_granted_by_fk_ix ON commerce.staff_role_assignment (granted_by);

CREATE INDEX address_party_id_ix ON commerce.address (party_id);

CREATE INDEX address_organization_id_ix ON commerce.address (organization_id);

CREATE UNIQUE INDEX address_party_id_uq ON commerce.address (party_id) WHERE is_default AND deleted_at IS NULL;

CREATE INDEX address_party_id_fk_ix ON commerce.address (party_id);

CREATE INDEX address_organization_id_fk_ix ON commerce.address (organization_id);

ALTER TABLE commerce.brand ADD CONSTRAINT brand_slug_uq UNIQUE (slug);

ALTER TABLE commerce.category ADD CONSTRAINT category_slug_uq UNIQUE (slug);

CREATE INDEX category_parent_id_ix ON commerce.category (parent_id);

CREATE INDEX category_parent_id_fk_ix ON commerce.category (parent_id);

ALTER TABLE commerce.product ADD COLUMN search_vector tsvector GENERATED ALWAYS AS (to_tsvector('simple'::regconfig, coalesce(name,'') || ' ' || coalesce(description,''))) STORED;

ALTER TABLE commerce.product ADD CONSTRAINT product_slug_uq UNIQUE (slug);

CREATE INDEX product_search_vector_ix ON commerce.product USING gin (search_vector);

CREATE INDEX product_status_published_at_ix ON commerce.product (status,published_at);

CREATE INDEX product_brand_id_fk_ix ON commerce.product (brand_id);

ALTER TABLE commerce.sku ADD CONSTRAINT sku_code_uq UNIQUE (code);

CREATE INDEX sku_product_id_ix ON commerce.sku (product_id);

CREATE INDEX sku_product_id_fk_ix ON commerce.sku (product_id);

CREATE INDEX sku_product_id_active_ix ON commerce.sku (product_id,active);

ALTER TABLE commerce.product_category ADD CONSTRAINT product_category_product_id_category_id_uq UNIQUE (product_id,category_id);

CREATE INDEX product_category_category_id_product_id_ix ON commerce.product_category (category_id,product_id);

CREATE INDEX product_category_product_id_fk_ix ON commerce.product_category (product_id);

CREATE INDEX product_category_category_id_fk_ix ON commerce.product_category (category_id);

ALTER TABLE commerce.product_attribute ADD CONSTRAINT product_attribute_product_id_attribute_key_uq UNIQUE (product_id,attribute_key);

CREATE INDEX product_attribute_product_id_fk_ix ON commerce.product_attribute (product_id);

ALTER TABLE commerce.asset_reference ADD CONSTRAINT asset_reference_object_key_uq UNIQUE (object_key);

CREATE INDEX asset_reference_product_id_purpose_ix ON commerce.asset_reference (product_id,purpose);

CREATE INDEX asset_reference_product_id_fk_ix ON commerce.asset_reference (product_id);

CREATE INDEX asset_reference_sku_id_fk_ix ON commerce.asset_reference (sku_id);

CREATE INDEX asset_reference_shipment_id_fk_ix ON commerce.asset_reference (shipment_id);

ALTER TABLE commerce.currency ADD CONSTRAINT currency_code_uq UNIQUE (code);

ALTER TABLE commerce.market ADD CONSTRAINT market_code_uq UNIQUE (code);

CREATE INDEX market_currency_code_fk_ix ON commerce.market (currency_code);

CREATE INDEX market_retail_price_book_id_fk_ix ON commerce.market (retail_price_book_id);

ALTER TABLE commerce.market_destination ADD CONSTRAINT market_destination_market_id_country_code_uq UNIQUE (market_id,country_code);

CREATE INDEX market_destination_market_id_fk_ix ON commerce.market_destination (market_id);

ALTER TABLE commerce.product_market_eligibility ADD CONSTRAINT product_market_eligibility_product_id_market_id_uq UNIQUE (product_id,market_id);

CREATE INDEX product_market_eligibility_product_id_fk_ix ON commerce.product_market_eligibility (product_id);

CREATE INDEX product_market_eligibility_market_id_fk_ix ON commerce.product_market_eligibility (market_id);

CREATE INDEX product_market_eligibility_market_id_product_id_ix ON commerce.product_market_eligibility (market_id,product_id);

ALTER TABLE commerce.price_book ADD CONSTRAINT price_book_code_revision_uq UNIQUE (code,revision);

CREATE INDEX price_book_segment_currency_code_effective_from_ix ON commerce.price_book (segment,currency_code,effective_from);

CREATE INDEX price_book_currency_code_fk_ix ON commerce.price_book (currency_code);

ALTER TABLE commerce.price_entry ADD CONSTRAINT price_entry_price_book_id_sku_id_minimum_quantity_uq UNIQUE (price_book_id,sku_id,minimum_quantity);

CREATE INDEX price_entry_sku_id_price_book_id_ix ON commerce.price_entry (sku_id,price_book_id);

CREATE INDEX price_entry_price_book_id_fk_ix ON commerce.price_entry (price_book_id);

CREATE INDEX price_entry_sku_id_fk_ix ON commerce.price_entry (sku_id);

CREATE INDEX delivery_rule_market_id_country_code_priority_ix ON commerce.delivery_rule (market_id,country_code,priority);

CREATE INDEX delivery_rule_market_id_fk_ix ON commerce.delivery_rule (market_id);

CREATE INDEX cart_party_id_status_ix ON commerce.cart (party_id,status);

CREATE INDEX cart_status_last_activity_at_ix ON commerce.cart (status,last_activity_at);

CREATE INDEX cart_party_id_fk_ix ON commerce.cart (party_id);

CREATE INDEX cart_organization_id_fk_ix ON commerce.cart (organization_id);

CREATE INDEX cart_market_id_fk_ix ON commerce.cart (market_id);

CREATE INDEX cart_converted_order_id_fk_ix ON commerce.cart (converted_order_id);

CREATE UNIQUE INDEX cart_active_context_uq ON commerce.cart (party_id,coalesce(organization_id,'00000000-0000-0000-0000-000000000000'::uuid),market_id) WHERE status='ACTIVE';

ALTER TABLE commerce.cart_line ADD CONSTRAINT cart_line_cart_id_sku_id_uq UNIQUE (cart_id,sku_id);

CREATE INDEX cart_line_cart_id_fk_ix ON commerce.cart_line (cart_id);

CREATE INDEX cart_line_sku_id_fk_ix ON commerce.cart_line (sku_id);

CREATE INDEX checkout_quote_cart_id_expires_at_ix ON commerce.checkout_quote (cart_id,expires_at);

CREATE INDEX checkout_quote_cart_id_fk_ix ON commerce.checkout_quote (cart_id);

CREATE INDEX checkout_quote_carrier_quote_id_fk_ix ON commerce.checkout_quote (carrier_quote_id);

ALTER TABLE commerce.sales_order ADD CONSTRAINT sales_order_order_number_uq UNIQUE (order_number);

CREATE UNIQUE INDEX sales_order_cart_id_uq ON commerce.sales_order (cart_id) WHERE cart_id IS NOT NULL;

CREATE INDEX sales_order_party_id_created_at_ix ON commerce.sales_order (party_id,created_at);

CREATE INDEX sales_order_organization_id_created_at_ix ON commerce.sales_order (organization_id,created_at);

CREATE INDEX sales_order_status_created_at_ix ON commerce.sales_order (status,created_at);

CREATE INDEX sales_order_party_id_fk_ix ON commerce.sales_order (party_id);

CREATE INDEX sales_order_organization_id_fk_ix ON commerce.sales_order (organization_id);

CREATE INDEX sales_order_market_id_fk_ix ON commerce.sales_order (market_id);

CREATE INDEX sales_order_cart_id_fk_ix ON commerce.sales_order (cart_id);

CREATE INDEX sales_order_currency_code_fk_ix ON commerce.sales_order (currency_code);

CREATE INDEX sales_order_accepted_quote_revision_id_fk_ix ON commerce.sales_order (accepted_quote_revision_id);

CREATE INDEX sales_order_carrier_quote_id_fk_ix ON commerce.sales_order (carrier_quote_id);

CREATE INDEX order_line_order_id_ix ON commerce.order_line (order_id);

CREATE INDEX order_line_order_id_fk_ix ON commerce.order_line (order_id);

CREATE INDEX order_line_sku_id_fk_ix ON commerce.order_line (sku_id);

CREATE INDEX order_total_component_order_id_ix ON commerce.order_total_component (order_id);

CREATE INDEX order_total_component_order_id_fk_ix ON commerce.order_total_component (order_id);

CREATE INDEX order_status_history_order_id_created_at_ix ON commerce.order_status_history (order_id,created_at);

CREATE INDEX order_status_history_order_id_fk_ix ON commerce.order_status_history (order_id);

ALTER TABLE commerce.stock_location ADD CONSTRAINT stock_location_code_uq UNIQUE (code);

ALTER TABLE commerce.inventory_balance ADD CONSTRAINT inventory_balance_sku_id_location_id_uq UNIQUE (sku_id,location_id);

CREATE INDEX inventory_balance_sku_id_fk_ix ON commerce.inventory_balance (sku_id);

CREATE INDEX inventory_balance_location_id_fk_ix ON commerce.inventory_balance (location_id);

ALTER TABLE commerce.inventory_reservation ADD CONSTRAINT inventory_reservation_order_id_balance_id_uq UNIQUE (order_id,balance_id);

CREATE INDEX inventory_reservation_status_expires_at_ix ON commerce.inventory_reservation (status,expires_at);

CREATE INDEX inventory_reservation_order_id_fk_ix ON commerce.inventory_reservation (order_id);

CREATE INDEX inventory_reservation_balance_id_fk_ix ON commerce.inventory_reservation (balance_id);

ALTER TABLE commerce.inventory_movement ADD CONSTRAINT inventory_movement_operation_key_uq UNIQUE (operation_key);

CREATE INDEX inventory_movement_balance_id_created_at_ix ON commerce.inventory_movement (balance_id,created_at);

CREATE INDEX inventory_movement_balance_id_fk_ix ON commerce.inventory_movement (balance_id);

CREATE INDEX inventory_movement_order_id_fk_ix ON commerce.inventory_movement (order_id);

CREATE INDEX inventory_movement_order_line_id_fk_ix ON commerce.inventory_movement (order_line_id);

ALTER TABLE commerce.payment_attempt ADD CONSTRAINT payment_attempt_provider_merchant_account_reference_uq UNIQUE (provider,merchant_account,reference);

CREATE INDEX payment_attempt_state_next_reconcile_at_ix ON commerce.payment_attempt (state,next_reconcile_at);

CREATE UNIQUE INDEX payment_attempt_order_id_uq ON commerce.payment_attempt (order_id) WHERE state IN ('CREATED','INITIATING','PENDING','UNKNOWN');

CREATE INDEX payment_attempt_order_id_fk_ix ON commerce.payment_attempt (order_id);

CREATE INDEX payment_attempt_currency_code_fk_ix ON commerce.payment_attempt (currency_code);

ALTER TABLE commerce.payment_transaction ADD CONSTRAINT payment_transaction_attempt_id_uq UNIQUE (attempt_id);

ALTER TABLE commerce.payment_transaction ADD CONSTRAINT payment_transaction_provider_transaction_id_uq UNIQUE (provider_transaction_id);

CREATE INDEX payment_transaction_attempt_id_fk_ix ON commerce.payment_transaction (attempt_id);

CREATE INDEX payment_transaction_currency_code_fk_ix ON commerce.payment_transaction (currency_code);

ALTER TABLE commerce.webhook_receipt ADD CONSTRAINT webhook_receipt_provider_merchant_account_event_key_uq UNIQUE (provider,merchant_account,event_key);

CREATE INDEX webhook_receipt_state_received_at_ix ON commerce.webhook_receipt (state,received_at);

ALTER TABLE commerce.refund ADD CONSTRAINT refund_request_key_uq UNIQUE (request_key);

ALTER TABLE commerce.refund ADD CONSTRAINT refund_provider_reference_uq UNIQUE (provider_reference);

CREATE UNIQUE INDEX refund_provider_refund_id_uq ON commerce.refund (provider_refund_id) WHERE provider_refund_id IS NOT NULL;

CREATE INDEX refund_state_created_at_ix ON commerce.refund (state,created_at);

CREATE INDEX refund_order_id_fk_ix ON commerce.refund (order_id);

CREATE INDEX refund_attempt_id_fk_ix ON commerce.refund (attempt_id);

CREATE INDEX refund_currency_code_fk_ix ON commerce.refund (currency_code);

CREATE INDEX refund_requested_by_fk_ix ON commerce.refund (requested_by);

CREATE INDEX refund_approved_by_fk_ix ON commerce.refund (approved_by);

ALTER TABLE commerce.refund_line ADD CONSTRAINT refund_line_refund_id_order_line_id_uq UNIQUE (refund_id,order_line_id);

CREATE INDEX refund_line_refund_id_fk_ix ON commerce.refund_line (refund_id);

CREATE INDEX refund_line_order_line_id_fk_ix ON commerce.refund_line (order_line_id);

CREATE INDEX shipment_order_id_ix ON commerce.shipment (order_id);

CREATE UNIQUE INDEX shipment_provider_provider_reference_uq ON commerce.shipment (provider,provider_reference) WHERE provider_reference IS NOT NULL;

CREATE INDEX shipment_order_id_fk_ix ON commerce.shipment (order_id);

CREATE INDEX shipment_label_asset_id_fk_ix ON commerce.shipment (label_asset_id);

ALTER TABLE commerce.shipment_line ADD CONSTRAINT shipment_line_shipment_id_order_line_id_balance_id_uq UNIQUE (shipment_id,order_line_id,balance_id);

CREATE INDEX shipment_line_shipment_id_fk_ix ON commerce.shipment_line (shipment_id);

CREATE INDEX shipment_line_order_line_id_fk_ix ON commerce.shipment_line (order_line_id);

CREATE INDEX shipment_line_balance_id_fk_ix ON commerce.shipment_line (balance_id);

ALTER TABLE commerce.shipment_event ADD CONSTRAINT shipment_event_source_event_key_uq UNIQUE (source,event_key);

CREATE INDEX shipment_event_shipment_id_occurred_at_ix ON commerce.shipment_event (shipment_id,occurred_at);

CREATE INDEX shipment_event_shipment_id_fk_ix ON commerce.shipment_event (shipment_id);

ALTER TABLE commerce.outbox_event ADD CONSTRAINT outbox_event_event_id_uq UNIQUE (event_id);

CREATE INDEX outbox_event_state_next_attempt_at_ix ON commerce.outbox_event (state,next_attempt_at);

ALTER TABLE commerce.processed_event ADD CONSTRAINT processed_event_consumer_event_id_uq UNIQUE (consumer,event_id);

ALTER TABLE commerce.idempotency_record ADD CONSTRAINT idempotency_record_principal_key_operation_request_key_uq UNIQUE (principal_key,operation,request_key);

CREATE INDEX idempotency_record_expires_at_ix ON commerce.idempotency_record (expires_at);

CREATE INDEX audit_record_resource_type_resource_id_occurred_at_ix ON commerce.audit_record (resource_type,resource_id,occurred_at);

CREATE INDEX audit_record_actor_subject_occurred_at_ix ON commerce.audit_record (actor_subject,occurred_at);

ALTER TABLE commerce.feature_configuration ADD CONSTRAINT feature_configuration_key_revision_uq UNIQUE (key,revision);

CREATE INDEX carrier_quote_cart_id_expires_at_ix ON commerce.carrier_quote (cart_id,expires_at);

ALTER TABLE commerce.carrier_quote ADD CONSTRAINT carrier_quote_cart_id_cart_version_address_hash_packag_b991cb26 UNIQUE (cart_id,cart_version,address_hash,package_hash,service_code,mode);

CREATE INDEX carrier_quote_cart_id_fk_ix ON commerce.carrier_quote (cart_id);

CREATE INDEX carrier_quote_market_id_fk_ix ON commerce.carrier_quote (market_id);

CREATE INDEX carrier_quote_currency_code_fk_ix ON commerce.carrier_quote (currency_code);

CREATE INDEX carrier_quote_source_quote_revision_id_fk_ix ON commerce.carrier_quote (source_quote_revision_id);
