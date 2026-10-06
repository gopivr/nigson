DO $$ BEGIN IF EXISTS (SELECT 1 FROM integration.outbox_event) THEN RAISE EXCEPTION 'Populated table integration.outbox_event: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM integration.processed_event) THEN RAISE EXCEPTION 'Populated table integration.processed_event: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM integration.idempotency_record) THEN RAISE EXCEPTION 'Populated table integration.idempotency_record: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM integration.audit_record) THEN RAISE EXCEPTION 'Populated table integration.audit_record: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM integration.feature_configuration) THEN RAISE EXCEPTION 'Populated table integration.feature_configuration: use expand/contract migration'; END IF; END $$;

DROP TABLE integration.feature_configuration;

DROP TABLE integration.audit_record;

DROP TABLE integration.idempotency_record;

DROP TABLE integration.processed_event;

DROP TABLE integration.outbox_event;
