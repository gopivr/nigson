-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE TABLE commerce.abandoned_cart_candidate (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    cart_id uuid NOT NULL,
    party_id uuid NOT NULL,
    scheduled_at timestamptz NOT NULL,
    status varchar(20) NOT NULL,
    notification_key varchar(160) NOT NULL,
    completed_at timestamptz,
    CONSTRAINT abandoned_cart_candidate_id_pk PRIMARY KEY (id),
    CONSTRAINT abandoned_cart_candidate_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT abandoned_cart_candidate_version_ck CHECK (version >= 0),
    CONSTRAINT abandoned_cart_candidate_cart_id_nonnil_ck CHECK (cart_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT abandoned_cart_candidate_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT abandoned_cart_candidate_rule_1_ck CHECK (status IN ('ELIGIBLE','REQUESTED','SUPPRESSED','COMPLETED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.abandoned_cart_candidate TO nigson_commerce_app;
