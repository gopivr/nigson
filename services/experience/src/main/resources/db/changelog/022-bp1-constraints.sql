-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE experience.content_item ADD CONSTRAINT content_item_current_published_revision_id_fk FOREIGN KEY (current_published_revision_id) REFERENCES experience.content_revision (id) ON DELETE RESTRICT;

ALTER TABLE experience.content_revision ADD CONSTRAINT content_revision_content_item_id_fk FOREIGN KEY (content_item_id) REFERENCES experience.content_item (id) ON DELETE RESTRICT;

ALTER TABLE experience.lead_activity ADD CONSTRAINT lead_activity_lead_id_fk FOREIGN KEY (lead_id) REFERENCES experience.lead (id) ON DELETE RESTRICT;

ALTER TABLE experience.destination_verification ADD CONSTRAINT destination_verification_destination_id_fk FOREIGN KEY (destination_id) REFERENCES experience.communication_destination (id) ON DELETE RESTRICT;

ALTER TABLE experience.inbound_message ADD CONSTRAINT inbound_message_lead_id_fk FOREIGN KEY (lead_id) REFERENCES experience.lead (id) ON DELETE RESTRICT;
