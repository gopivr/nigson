DO $$ BEGIN IF EXISTS (SELECT 1 FROM ai.outbox_event) THEN RAISE EXCEPTION 'Populated table ai.outbox_event: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.processed_event) THEN RAISE EXCEPTION 'Populated table ai.processed_event: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.idempotency_record) THEN RAISE EXCEPTION 'Populated table ai.idempotency_record: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.audit_record) THEN RAISE EXCEPTION 'Populated table ai.audit_record: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM ai.feature_configuration) THEN RAISE EXCEPTION 'Populated table ai.feature_configuration: use expand/contract migration'; END IF; END $$;

DROP TABLE ai.feature_configuration;

DROP TABLE ai.audit_record;

DROP TABLE ai.idempotency_record;

DROP TABLE ai.processed_event;

DROP TABLE ai.outbox_event;
