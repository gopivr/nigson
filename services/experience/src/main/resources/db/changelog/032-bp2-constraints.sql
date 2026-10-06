-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE experience.file_asset ADD CONSTRAINT file_asset_upload_session_id_fk FOREIGN KEY (upload_session_id) REFERENCES experience.upload_session (id) ON DELETE RESTRICT;

ALTER TABLE experience.career_application ADD CONSTRAINT career_application_opportunity_id_fk FOREIGN KEY (opportunity_id) REFERENCES experience.career_opportunity (id) ON DELETE RESTRICT;

ALTER TABLE experience.career_application ADD CONSTRAINT career_application_cv_file_id_fk FOREIGN KEY (cv_file_id) REFERENCES experience.file_asset (id) ON DELETE RESTRICT;

ALTER TABLE experience.upload_session ADD CONSTRAINT upload_session_claimed_application_id_fk FOREIGN KEY (claimed_application_id) REFERENCES experience.career_application (id) ON DELETE RESTRICT;
