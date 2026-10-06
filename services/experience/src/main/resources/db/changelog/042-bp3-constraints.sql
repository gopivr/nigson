-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE experience.application_document ADD CONSTRAINT application_document_application_id_fk FOREIGN KEY (application_id) REFERENCES experience.distributor_application (id) ON DELETE RESTRICT;

ALTER TABLE experience.application_document ADD CONSTRAINT application_document_file_asset_id_fk FOREIGN KEY (file_asset_id) REFERENCES experience.file_asset (id) ON DELETE RESTRICT;

ALTER TABLE experience.lead_requested_item ADD CONSTRAINT lead_requested_item_lead_id_fk FOREIGN KEY (lead_id) REFERENCES experience.lead (id) ON DELETE RESTRICT;
