-- ADR-031: reserved monthly manual affiliate payouts, no automated transfer.
CREATE TABLE commerce.affiliate_payout (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid NOT NULL,
    bank_detail_id uuid NOT NULL,
    period char(7) NOT NULL,
    state varchar(20) NOT NULL,
    amount numeric(19,2) NOT NULL,
    transfer_reference varchar(120),
    paid_at timestamptz,
    resolved_by uuid,
    cancel_reason varchar(500),
    CONSTRAINT affiliate_payout_id_pk PRIMARY KEY (id),
    CONSTRAINT affiliate_payout_version_ck CHECK (version >= 0),
    CONSTRAINT affiliate_payout_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_payout_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_payout_bank_detail_id_nonnil_ck CHECK (bank_detail_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_payout_resolved_by_nonnil_ck CHECK (resolved_by <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_payout_rule_1_ck CHECK (amount>0),
    CONSTRAINT affiliate_payout_rule_2_ck CHECK (state IN ('PREPARED','PAID','CANCELLED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.affiliate_payout TO nigson_commerce_app;

CREATE UNIQUE INDEX affiliate_payout_party_id_period_uq ON commerce.affiliate_payout (party_id,period) WHERE state IN ('PREPARED','PAID');

CREATE UNIQUE INDEX affiliate_payout_party_id_uq ON commerce.affiliate_payout (party_id) WHERE state='PREPARED';

CREATE UNIQUE INDEX affiliate_payout_transfer_reference_uq ON commerce.affiliate_payout (transfer_reference);

CREATE INDEX affiliate_payout_party_id_created_at_ix ON commerce.affiliate_payout (party_id,created_at);

ALTER TABLE commerce.affiliate_payout ADD CONSTRAINT affiliate_payout_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.affiliate_payout ADD CONSTRAINT affiliate_payout_bank_detail_id_fk FOREIGN KEY (bank_detail_id) REFERENCES commerce.affiliate_bank_detail (id) ON DELETE RESTRICT;

ALTER TABLE commerce.affiliate_payout ADD CONSTRAINT affiliate_payout_resolved_by_fk FOREIGN KEY (resolved_by) REFERENCES commerce.party (id) ON DELETE RESTRICT;
