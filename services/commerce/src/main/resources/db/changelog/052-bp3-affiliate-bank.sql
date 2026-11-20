-- ADR-031: encrypted reviewed affiliate bank revisions.
CREATE TABLE commerce.affiliate_bank_detail (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid NOT NULL,
    revision bigint NOT NULL,
    previous_id uuid,
    payload_ciphertext bytea NOT NULL,
    content_hash char(64) NOT NULL,
    cipher_mode varchar(10) NOT NULL,
    status varchar(20) NOT NULL,
    reviewed_by uuid,
    reviewed_at timestamptz,
    review_reason varchar(500),
    CONSTRAINT affiliate_bank_detail_id_pk PRIMARY KEY (id),
    CONSTRAINT affiliate_bank_detail_version_ck CHECK (version >= 0),
    CONSTRAINT affiliate_bank_detail_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_bank_detail_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_bank_detail_previous_id_nonnil_ck CHECK (previous_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_bank_detail_reviewed_by_nonnil_ck CHECK (reviewed_by <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_bank_detail_rule_1_ck CHECK (revision>0),
    CONSTRAINT affiliate_bank_detail_rule_2_ck CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT affiliate_bank_detail_rule_3_ck CHECK (cipher_mode IN ('MOCK','TEST','LIVE'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.affiliate_bank_detail TO nigson_commerce_app;

CREATE UNIQUE INDEX affiliate_bank_detail_party_id_revision_uq ON commerce.affiliate_bank_detail (party_id,revision);

ALTER TABLE commerce.affiliate_bank_detail ADD CONSTRAINT affiliate_bank_detail_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.affiliate_bank_detail ADD CONSTRAINT affiliate_bank_detail_previous_id_fk FOREIGN KEY (previous_id) REFERENCES commerce.affiliate_bank_detail (id) ON DELETE RESTRICT;

ALTER TABLE commerce.affiliate_bank_detail ADD CONSTRAINT affiliate_bank_detail_reviewed_by_fk FOREIGN KEY (reviewed_by) REFERENCES commerce.party (id) ON DELETE RESTRICT;
