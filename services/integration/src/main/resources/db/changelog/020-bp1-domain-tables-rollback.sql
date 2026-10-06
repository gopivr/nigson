DO $$ BEGIN IF EXISTS (SELECT 1 FROM integration.notification_template) THEN RAISE EXCEPTION 'Populated table integration.notification_template: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM integration.notification_job) THEN RAISE EXCEPTION 'Populated table integration.notification_job: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM integration.notification_attempt) THEN RAISE EXCEPTION 'Populated table integration.notification_attempt: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM integration.integration_job) THEN RAISE EXCEPTION 'Populated table integration.integration_job: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM integration.provider_cursor) THEN RAISE EXCEPTION 'Populated table integration.provider_cursor: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM integration.carrier_booking) THEN RAISE EXCEPTION 'Populated table integration.carrier_booking: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM integration.message_receipt) THEN RAISE EXCEPTION 'Populated table integration.message_receipt: use expand/contract migration'; END IF; END $$;

DROP TABLE integration.message_receipt;

DROP TABLE integration.carrier_booking;

DROP TABLE integration.provider_cursor;

DROP TABLE integration.integration_job;

DROP TABLE integration.notification_attempt;

DROP TABLE integration.notification_job;

DROP TABLE integration.notification_template;
