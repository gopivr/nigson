-- ADR-031: seven-day same-browser affiliate visits; order snapshots are immutable.
CREATE TABLE commerce.affiliate_visit (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    link_id uuid NOT NULL,
    clicked_at timestamptz NOT NULL,
    expires_at timestamptz NOT NULL,
    CONSTRAINT affiliate_visit_id_pk PRIMARY KEY (id),
    CONSTRAINT affiliate_visit_version_ck CHECK (version >= 0),
    CONSTRAINT affiliate_visit_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_visit_link_id_nonnil_ck CHECK (link_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT affiliate_visit_rule_1_ck CHECK (expires_at>clicked_at)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.affiliate_visit TO nigson_commerce_app;

CREATE INDEX affiliate_visit_expires_at_ix ON commerce.affiliate_visit (expires_at);

ALTER TABLE commerce.affiliate_visit ADD CONSTRAINT affiliate_visit_link_id_fk FOREIGN KEY (link_id) REFERENCES commerce.affiliate_link (id) ON DELETE RESTRICT;
