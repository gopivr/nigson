-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE integration.sheet_sync_record ADD CONSTRAINT sheet_sync_record_application_id_spreadsheet_key_uq UNIQUE (application_id,spreadsheet_key);

ALTER TABLE integration.sheet_sync_record ADD CONSTRAINT sheet_sync_record_spreadsheet_key_row_key_uq UNIQUE (spreadsheet_key,row_key);
