DO $$ BEGIN IF EXISTS (SELECT 1 FROM integration.notification_dispatch) THEN RAISE EXCEPTION 'Populated table integration.notification_dispatch: use expand/contract migration'; END IF; END $$;

ALTER TABLE integration.notification_dispatch DROP CONSTRAINT notification_dispatch_template_id_fk;

ALTER TABLE integration.notification_dispatch DROP CONSTRAINT notification_dispatch_id_fk;

DROP INDEX integration.notification_dispatch_stage_next_attempt_at_ix;

DROP INDEX integration.notification_dispatch_account_sid_provider_message_id_uq;

DROP TABLE integration.notification_dispatch;
