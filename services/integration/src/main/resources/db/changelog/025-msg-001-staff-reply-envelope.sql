-- ADR-020: encrypted staff-reply destination and variables stay Integration-owned.
ALTER TABLE integration.notification_dispatch ADD COLUMN command_hash char(64);
ALTER TABLE integration.notification_dispatch ADD COLUMN recipient_ciphertext bytea;
ALTER TABLE integration.notification_dispatch ADD COLUMN variables_ciphertext bytea;
ALTER TABLE integration.notification_dispatch ADD COLUMN context_key_revision varchar(40);
ALTER TABLE integration.notification_dispatch ADD COLUMN context_basis varchar(40);
ALTER TABLE integration.notification_dispatch ADD CONSTRAINT notification_dispatch_staff_context_ck CHECK ((command_hash IS NULL AND recipient_ciphertext IS NULL AND variables_ciphertext IS NULL AND context_key_revision IS NULL AND context_basis IS NULL) OR (command_hash IS NOT NULL AND command_hash ~ '^[a-f0-9]{64}$' AND recipient_ciphertext IS NOT NULL AND octet_length(recipient_ciphertext) BETWEEN 29 AND 512 AND variables_ciphertext IS NOT NULL AND octet_length(variables_ciphertext) BETWEEN 29 AND 8192 AND context_key_revision IS NOT NULL AND context_key_revision ~ '^[A-Za-z0-9_-]{1,40}$' AND context_basis IS NOT NULL AND context_basis IN ('SIGNED_INBOUND_REQUEST','VERIFIED_DESTINATION')));
