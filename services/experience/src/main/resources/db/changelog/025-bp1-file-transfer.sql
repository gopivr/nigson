-- EXP-FILES-001: Experience-owned pinned file scan/promotion intent; existing migrations preserved.
CREATE TABLE experience.file_transfer (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    owner_party_id uuid NOT NULL,
    source_key varchar(500) NOT NULL,
    final_key varchar(500) NOT NULL,
    upload_expires_at timestamptz NOT NULL,
    scan_command_id uuid,
    source_version varchar(200),
    source_etag varchar(200),
    state varchar(20) NOT NULL DEFAULT 'UPLOADING',
    copy_lease_until timestamptz,
    scan_request_hash char(64),
    scan_result varchar(8),
    scanner_version varchar(100),
    CONSTRAINT file_transfer_id_pk PRIMARY KEY (id),
    CONSTRAINT file_transfer_version_ck CHECK (version >= 0),
    CONSTRAINT file_transfer_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT file_transfer_owner_party_id_nonnil_ck CHECK (owner_party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT file_transfer_scan_command_id_nonnil_ck CHECK (scan_command_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT file_transfer_rule_1_ck CHECK (state IN ('UPLOADING','QUARANTINED','COPYING','UNKNOWN','CLEAN','REJECTED')),
    CONSTRAINT file_transfer_rule_2_ck CHECK (scan_result IN ('CLEAN','REJECTED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.file_transfer TO nigson_experience_app;

CREATE UNIQUE INDEX file_transfer_scan_command_id_uq ON experience.file_transfer (scan_command_id);

CREATE INDEX file_transfer_owner_party_id_ix ON experience.file_transfer (owner_party_id);

CREATE INDEX file_transfer_state_updated_at_ix ON experience.file_transfer (state,updated_at);

ALTER TABLE experience.file_transfer ADD CONSTRAINT file_transfer_id_fk FOREIGN KEY (id) REFERENCES experience.file_asset (id) ON DELETE RESTRICT;
