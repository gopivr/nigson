-- ADR-031: immutable verified retail affiliate earnings and refund reversals.
CREATE TABLE commerce.affiliate_commission_entry (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    party_id uuid NOT NULL,
    order_id uuid NOT NULL,
    kind varchar(20) NOT NULL,
    amount numeric(19,2) NOT NULL,
    merchandise_basis numeric(19,2) NOT NULL,
    rate numeric(5,4) NOT NULL,
    business_key varchar(200) NOT NULL,
    reverses_id uuid,
    refund_id uuid,
    CONSTRAINT affiliate_commission_entry_id_pk PRIMARY KEY (id),
    CONSTRAINT affiliate_commission_entry_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_commission_entry_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_commission_entry_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_commission_entry_reverses_id_nonnil_ck CHECK (reverses_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_commission_entry_refund_id_nonnil_ck CHECK (refund_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_commission_entry_rule_1_ck CHECK (kind IN ('EARN','REVERSE')),
    CONSTRAINT affiliate_commission_entry_rule_2_ck CHECK (amount>0),
    CONSTRAINT affiliate_commission_entry_rule_3_ck CHECK (merchandise_basis>=0),
    CONSTRAINT affiliate_commission_entry_rule_4_ck CHECK (rate=0.05)
);

GRANT SELECT, INSERT ON commerce.affiliate_commission_entry TO nigson_commerce_app;

CREATE UNIQUE INDEX affiliate_commission_entry_business_key_uq ON commerce.affiliate_commission_entry (business_key);

CREATE INDEX affiliate_commission_entry_party_id_created_at_ix ON commerce.affiliate_commission_entry (party_id,created_at);

CREATE INDEX affiliate_commission_entry_order_id_ix ON commerce.affiliate_commission_entry (order_id);

ALTER TABLE commerce.affiliate_commission_entry ADD CONSTRAINT affiliate_commission_entry_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.affiliate_commission_entry ADD CONSTRAINT affiliate_commission_entry_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.affiliate_commission_entry ADD CONSTRAINT affiliate_commission_entry_reverses_id_fk FOREIGN KEY (reverses_id) REFERENCES commerce.affiliate_commission_entry (id) ON DELETE RESTRICT;

ALTER TABLE commerce.affiliate_commission_entry ADD CONSTRAINT affiliate_commission_entry_refund_id_fk FOREIGN KEY (refund_id) REFERENCES commerce.refund (id) ON DELETE RESTRICT;
