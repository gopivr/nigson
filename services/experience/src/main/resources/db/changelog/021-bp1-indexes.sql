-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE experience.content_item ADD CONSTRAINT content_item_content_type_slug_locale_uq UNIQUE (content_type,slug,locale);

CREATE INDEX content_item_current_published_revision_id_fk_ix ON experience.content_item (current_published_revision_id);

ALTER TABLE experience.content_revision ADD CONSTRAINT content_revision_content_item_id_revision_uq UNIQUE (content_item_id,revision);

CREATE INDEX content_revision_state_scheduled_at_ix ON experience.content_revision (state,scheduled_at);

CREATE INDEX content_revision_content_item_id_fk_ix ON experience.content_revision (content_item_id);

ALTER TABLE experience.file_asset ADD CONSTRAINT file_asset_object_key_uq UNIQUE (object_key);

CREATE INDEX file_asset_owner_party_id_ix ON experience.file_asset (owner_party_id);

CREATE INDEX file_asset_scan_status_created_at_ix ON experience.file_asset (scan_status,created_at);

CREATE INDEX file_asset_upload_session_id_fk_ix ON experience.file_asset (upload_session_id);

CREATE INDEX lead_status_priority_created_at_ix ON experience.lead (status,priority,created_at);

CREATE INDEX lead_assigned_to_follow_up_at_ix ON experience.lead (assigned_to,follow_up_at);

CREATE INDEX lead_activity_lead_id_created_at_ix ON experience.lead_activity (lead_id,created_at);

CREATE INDEX lead_activity_lead_id_fk_ix ON experience.lead_activity (lead_id);

CREATE INDEX consent_event_email_hash_channel_purpose_occurred_at_ix ON experience.consent_event (email_hash,channel,purpose,occurred_at);

ALTER TABLE experience.communication_suppression ADD CONSTRAINT communication_suppression_destination_hash_channel_uq UNIQUE (destination_hash,channel);

CREATE INDEX communication_suppression_email_hash_channel_ix ON experience.communication_suppression (email_hash,channel);

ALTER TABLE experience.channel_configuration ADD CONSTRAINT channel_configuration_kind_key_uq UNIQUE (kind,key);

ALTER TABLE experience.outbox_event ADD CONSTRAINT outbox_event_event_id_uq UNIQUE (event_id);

CREATE INDEX outbox_event_state_next_attempt_at_ix ON experience.outbox_event (state,next_attempt_at);

ALTER TABLE experience.processed_event ADD CONSTRAINT processed_event_consumer_event_id_uq UNIQUE (consumer,event_id);

ALTER TABLE experience.idempotency_record ADD CONSTRAINT idempotency_record_principal_key_operation_request_key_uq UNIQUE (principal_key,operation,request_key);

CREATE INDEX idempotency_record_expires_at_ix ON experience.idempotency_record (expires_at);

CREATE INDEX audit_record_resource_type_resource_id_occurred_at_ix ON experience.audit_record (resource_type,resource_id,occurred_at);

CREATE INDEX audit_record_actor_subject_occurred_at_ix ON experience.audit_record (actor_subject,occurred_at);

ALTER TABLE experience.feature_configuration ADD CONSTRAINT feature_configuration_key_revision_uq UNIQUE (key,revision);

ALTER TABLE experience.communication_destination ADD CONSTRAINT communication_destination_party_id_channel_destination_hash_uq UNIQUE (party_id,channel,destination_hash);

CREATE INDEX communication_destination_destination_hash_channel_ix ON experience.communication_destination (destination_hash,channel);

CREATE UNIQUE INDEX communication_destination_party_id_channel_uq ON experience.communication_destination (party_id,channel) WHERE status='ACTIVE';

ALTER TABLE experience.destination_verification ADD CONSTRAINT destination_verification_sent_job_key_uq UNIQUE (sent_job_key);

CREATE INDEX destination_verification_destination_id_status_ix ON experience.destination_verification (destination_id,status);

CREATE UNIQUE INDEX destination_verification_destination_id_uq ON experience.destination_verification (destination_id) WHERE status='PENDING';

CREATE INDEX destination_verification_destination_id_fk_ix ON experience.destination_verification (destination_id);

ALTER TABLE experience.inbound_message ADD CONSTRAINT inbound_message_provider_provider_message_id_uq UNIQUE (provider,provider_message_id);

CREATE INDEX inbound_message_retention_until_ix ON experience.inbound_message (retention_until);

CREATE INDEX inbound_message_lead_id_fk_ix ON experience.inbound_message (lead_id);
