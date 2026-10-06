-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE INDEX distributor_application_party_id_submitted_at_ix ON experience.distributor_application (party_id,submitted_at);

CREATE UNIQUE INDEX distributor_application_decision_command_id_uq ON experience.distributor_application (decision_command_id) WHERE decision_command_id IS NOT NULL;

ALTER TABLE experience.application_document ADD CONSTRAINT application_document_application_id_file_asset_id_uq UNIQUE (application_id,file_asset_id);

CREATE INDEX application_document_application_id_fk_ix ON experience.application_document (application_id);

CREATE INDEX application_document_file_asset_id_fk_ix ON experience.application_document (file_asset_id);

ALTER TABLE experience.lead_requested_item ADD CONSTRAINT lead_requested_item_lead_id_sku_id_uq UNIQUE (lead_id,sku_id);

CREATE INDEX lead_requested_item_lead_id_fk_ix ON experience.lead_requested_item (lead_id);
