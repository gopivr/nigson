DO $$ BEGIN IF EXISTS (SELECT 1 FROM integration.notification_dispatch WHERE command_hash IS NOT NULL OR recipient_ciphertext IS NOT NULL OR variables_ciphertext IS NOT NULL OR context_key_revision IS NOT NULL OR context_basis IS NOT NULL) THEN RAISE EXCEPTION 'Populated staff-reply context: use expand/contract migration'; END IF; END $$;
ALTER TABLE integration.notification_dispatch DROP CONSTRAINT notification_dispatch_staff_context_ck;
ALTER TABLE integration.notification_dispatch DROP COLUMN context_basis;
ALTER TABLE integration.notification_dispatch DROP COLUMN context_key_revision;
ALTER TABLE integration.notification_dispatch DROP COLUMN variables_ciphertext;
ALTER TABLE integration.notification_dispatch DROP COLUMN recipient_ciphertext;
ALTER TABLE integration.notification_dispatch DROP COLUMN command_hash;
