-- ADR-031: approved affiliate product links.
CREATE TABLE commerce.affiliate_link (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid NOT NULL,
    product_id uuid NOT NULL,
    code varchar(32) NOT NULL,
    CONSTRAINT affiliate_link_id_pk PRIMARY KEY (id),
    CONSTRAINT affiliate_link_version_ck CHECK (version >= 0),
    CONSTRAINT affiliate_link_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_link_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_link_product_id_nonnil_ck CHECK (product_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_link_rule_1_ck CHECK (char_length(code)=32)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.affiliate_link TO nigson_commerce_app;

CREATE UNIQUE INDEX affiliate_link_party_id_product_id_uq ON commerce.affiliate_link (party_id,product_id);

CREATE UNIQUE INDEX affiliate_link_code_uq ON commerce.affiliate_link (code);

ALTER TABLE commerce.affiliate_link ADD CONSTRAINT affiliate_link_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.affiliate_link ADD CONSTRAINT affiliate_link_product_id_fk FOREIGN KEY (product_id) REFERENCES commerce.product (id) ON DELETE RESTRICT;
