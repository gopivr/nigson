-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE integration.notification_template ADD CONSTRAINT notification_template_code_revision_locale_channel_uq UNIQUE (code,revision,locale,channel);

ALTER TABLE integration.notification_job ADD CONSTRAINT notification_job_business_key_uq UNIQUE (business_key);

CREATE INDEX notification_job_state_next_attempt_at_ix ON integration.notification_job (state,next_attempt_at);

ALTER TABLE integration.notification_attempt ADD CONSTRAINT notification_attempt_job_id_attempt_number_uq UNIQUE (job_id,attempt_number);

CREATE INDEX notification_attempt_provider_message_id_ix ON integration.notification_attempt (provider_message_id);

CREATE INDEX notification_attempt_job_id_fk_ix ON integration.notification_attempt (job_id);

ALTER TABLE integration.integration_job ADD CONSTRAINT integration_job_business_key_uq UNIQUE (business_key);

CREATE INDEX integration_job_kind_state_next_attempt_at_ix ON integration.integration_job (kind,state,next_attempt_at);

ALTER TABLE integration.provider_cursor ADD CONSTRAINT provider_cursor_provider_job_kind_uq UNIQUE (provider,job_kind);

ALTER TABLE integration.outbox_event ADD CONSTRAINT outbox_event_event_id_uq UNIQUE (event_id);

CREATE INDEX outbox_event_state_next_attempt_at_ix ON integration.outbox_event (state,next_attempt_at);

ALTER TABLE integration.processed_event ADD CONSTRAINT processed_event_consumer_event_id_uq UNIQUE (consumer,event_id);

ALTER TABLE integration.idempotency_record ADD CONSTRAINT idempotency_record_principal_key_operation_request_key_uq UNIQUE (principal_key,operation,request_key);

CREATE INDEX idempotency_record_expires_at_ix ON integration.idempotency_record (expires_at);

CREATE INDEX audit_record_resource_type_resource_id_occurred_at_ix ON integration.audit_record (resource_type,resource_id,occurred_at);

CREATE INDEX audit_record_actor_subject_occurred_at_ix ON integration.audit_record (actor_subject,occurred_at);

ALTER TABLE integration.feature_configuration ADD CONSTRAINT feature_configuration_key_revision_uq UNIQUE (key,revision);

ALTER TABLE integration.carrier_booking ADD CONSTRAINT carrier_booking_shipment_id_provider_uq UNIQUE (shipment_id,provider);

ALTER TABLE integration.carrier_booking ADD CONSTRAINT carrier_booking_business_key_uq UNIQUE (business_key);

CREATE INDEX carrier_booking_state_next_poll_at_ix ON integration.carrier_booking (state,next_poll_at);

ALTER TABLE integration.message_receipt ADD CONSTRAINT message_receipt_provider_provider_message_id_event_hash_uq UNIQUE (provider,provider_message_id,event_hash);

CREATE INDEX message_receipt_job_id_received_at_ix ON integration.message_receipt (job_id,received_at);

CREATE INDEX message_receipt_job_id_fk_ix ON integration.message_receipt (job_id);
