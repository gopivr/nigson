DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.file_transfer) THEN RAISE EXCEPTION 'Populated table commerce.file_transfer: use expand/contract migration'; END IF; END $$;

ALTER TABLE commerce.file_transfer DROP CONSTRAINT file_transfer_owner_party_id_fk;

ALTER TABLE commerce.file_transfer DROP CONSTRAINT file_transfer_id_fk;

DROP INDEX commerce.file_transfer_state_updated_at_ix;

DROP INDEX commerce.file_transfer_owner_party_id_ix;

DROP INDEX commerce.file_transfer_scan_command_id_uq;

DROP TABLE commerce.file_transfer;
