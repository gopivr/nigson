DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.outbox_event) THEN RAISE EXCEPTION 'Populated table commerce.outbox_event: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.processed_event) THEN RAISE EXCEPTION 'Populated table commerce.processed_event: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.idempotency_record) THEN RAISE EXCEPTION 'Populated table commerce.idempotency_record: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.audit_record) THEN RAISE EXCEPTION 'Populated table commerce.audit_record: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.feature_configuration) THEN RAISE EXCEPTION 'Populated table commerce.feature_configuration: use expand/contract migration'; END IF; END $$;

DROP TABLE commerce.feature_configuration;

DROP TABLE commerce.audit_record;

DROP TABLE commerce.idempotency_record;

DROP TABLE commerce.processed_event;

DROP TABLE commerce.outbox_event;
