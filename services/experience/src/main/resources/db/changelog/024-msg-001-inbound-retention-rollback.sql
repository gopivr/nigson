DO $$ BEGIN IF EXISTS (SELECT 1 FROM experience.inbound_message WHERE sender_ciphertext IS NULL) THEN RAISE EXCEPTION 'Cannot restore inbound sender ciphertext NOT NULL while retention-erased records exist'; END IF; END $$;
ALTER TABLE experience.inbound_message ALTER COLUMN sender_ciphertext SET NOT NULL;
