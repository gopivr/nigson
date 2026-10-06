-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE TABLE integration.sheet_sync_record (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    application_id uuid NOT NULL,
    spreadsheet_key varchar(120) NOT NULL,
    row_key varchar(120) NOT NULL,
    content_hash char(64) NOT NULL,
    state varchar(20) NOT NULL,
    CONSTRAINT sheet_sync_record_id_pk PRIMARY KEY (id),
    CONSTRAINT sheet_sync_record_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT sheet_sync_record_version_ck CHECK (version >= 0),
    CONSTRAINT sheet_sync_record_application_id_nonnil_ck CHECK (application_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT sheet_sync_record_rule_1_ck CHECK (state IN ('PENDING','SYNCED','FAILED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON integration.sheet_sync_record TO nigson_integration_app;
