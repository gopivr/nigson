-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE TABLE commerce.product_recommendation (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    product_id uuid NOT NULL,
    related_product_id uuid NOT NULL,
    kind varchar(20) NOT NULL,
    priority integer NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT product_recommendation_id_pk PRIMARY KEY (id),
    CONSTRAINT product_recommendation_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT product_recommendation_version_ck CHECK (version >= 0),
    CONSTRAINT product_recommendation_product_id_nonnil_ck CHECK (product_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT product_recommendation_related_product_id_nonnil_ck CHECK (related_product_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT product_recommendation_rule_1_ck CHECK (product_id<>related_product_id),
    CONSTRAINT product_recommendation_rule_2_ck CHECK (kind IN ('RELATED','FEATURED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.product_recommendation TO nigson_commerce_app;
