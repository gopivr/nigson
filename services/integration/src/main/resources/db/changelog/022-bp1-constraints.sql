-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE integration.notification_attempt ADD CONSTRAINT notification_attempt_job_id_fk FOREIGN KEY (job_id) REFERENCES integration.notification_job (id) ON DELETE RESTRICT;

ALTER TABLE integration.message_receipt ADD CONSTRAINT message_receipt_job_id_fk FOREIGN KEY (job_id) REFERENCES integration.notification_job (id) ON DELETE RESTRICT;
