-- Public lead rate limiting stores only a keyed source hash; prior migrations preserved.
CREATE TABLE experience.lead_rate_limit (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    source_hash char(64) NOT NULL,
    window_start timestamptz NOT NULL,
    request_count integer NOT NULL,
    CONSTRAINT lead_rate_limit_id_pk PRIMARY KEY (id),
    CONSTRAINT lead_rate_limit_version_ck CHECK (version >= 0),
    CONSTRAINT lead_rate_limit_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT lead_rate_limit_rule_1_ck CHECK (source_hash ~ '^[a-f0-9]{64}$'),
    CONSTRAINT lead_rate_limit_rule_2_ck CHECK (request_count>0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.lead_rate_limit TO nigson_experience_app;

CREATE UNIQUE INDEX lead_rate_limit_source_hash_uq ON experience.lead_rate_limit (source_hash);

CREATE INDEX lead_rate_limit_window_start_ix ON experience.lead_rate_limit (window_start);
