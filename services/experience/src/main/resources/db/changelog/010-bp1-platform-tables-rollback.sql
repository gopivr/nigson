DO $$ BEGIN IF EXISTS (SELECT 1 FROM experience.outbox_event) THEN RAISE EXCEPTION 'Populated table experience.outbox_event: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.processed_event) THEN RAISE EXCEPTION 'Populated table experience.processed_event: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.idempotency_record) THEN RAISE EXCEPTION 'Populated table experience.idempotency_record: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.audit_record) THEN RAISE EXCEPTION 'Populated table experience.audit_record: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.feature_configuration) THEN RAISE EXCEPTION 'Populated table experience.feature_configuration: use expand/contract migration'; END IF; END $$;

DROP TABLE experience.feature_configuration;

DROP TABLE experience.audit_record;

DROP TABLE experience.idempotency_record;

DROP TABLE experience.processed_event;

DROP TABLE experience.outbox_event;
