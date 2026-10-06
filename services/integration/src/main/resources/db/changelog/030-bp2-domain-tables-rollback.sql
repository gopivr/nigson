DO $$ BEGIN IF EXISTS (SELECT 1 FROM integration.sheet_sync_record) THEN RAISE EXCEPTION 'Populated table integration.sheet_sync_record: use expand/contract migration'; END IF; END $$;

DROP TABLE integration.sheet_sync_record;
