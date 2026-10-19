DO $$ BEGIN IF EXISTS (SELECT 1 FROM experience.file_transfer) THEN RAISE EXCEPTION 'Populated table experience.file_transfer: use expand/contract migration'; END IF; END $$;

ALTER TABLE experience.file_transfer DROP CONSTRAINT file_transfer_id_fk;

DROP INDEX experience.file_transfer_state_updated_at_ix;

DROP INDEX experience.file_transfer_owner_party_id_ix;

DROP INDEX experience.file_transfer_scan_command_id_uq;

DROP TABLE experience.file_transfer;
