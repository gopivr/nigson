-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE TABLE experience.review (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    product_id uuid,
    order_line_id uuid,
    party_id uuid NOT NULL,
    kind varchar(20) NOT NULL,
    rating smallint,
    title varchar(160),
    body text NOT NULL,
    state varchar(20) NOT NULL,
    verified_purchase boolean NOT NULL DEFAULT false,
    moderation_reason varchar(500),
    CONSTRAINT review_id_pk PRIMARY KEY (id),
    CONSTRAINT review_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT review_version_ck CHECK (version >= 0),
    CONSTRAINT review_product_id_nonnil_ck CHECK (product_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT review_order_line_id_nonnil_ck CHECK (order_line_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT review_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT review_rule_1_ck CHECK (kind IN ('PRODUCT','TESTIMONIAL')),
    CONSTRAINT review_rule_2_ck CHECK (rating IS NULL OR rating BETWEEN 1 AND 5),
    CONSTRAINT review_rule_3_ck CHECK (state IN ('PENDING','PUBLISHED','REJECTED','WITHDRAWN'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.review TO nigson_experience_app;

CREATE TABLE experience.newsletter_subscription (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid,
    email_ciphertext bytea NOT NULL,
    email_hash char(64) NOT NULL,
    state varchar(20) NOT NULL,
    verified_at timestamptz,
    unsubscribe_token_hash char(64) NOT NULL,
    provider_contact_id varchar(120),
    confirmation_token_hash char(64),
    confirmation_expires_at timestamptz,
    encrypted_confirmation_context bytea,
    confirmation_context_expires_at timestamptz,
    CONSTRAINT newsletter_subscription_id_pk PRIMARY KEY (id),
    CONSTRAINT newsletter_subscription_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT newsletter_subscription_version_ck CHECK (version >= 0),
    CONSTRAINT newsletter_subscription_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT newsletter_subscription_rule_1_ck CHECK (state IN ('PENDING','ACTIVE','UNSUBSCRIBED','SUPPRESSED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.newsletter_subscription TO nigson_experience_app;

CREATE TABLE experience.career_opportunity (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    title varchar(180) NOT NULL,
    slug varchar(200) NOT NULL,
    kind varchar(30) NOT NULL,
    description text NOT NULL,
    fields_schema jsonb NOT NULL,
    state varchar(20) NOT NULL,
    closes_at timestamptz,
    deleted_at timestamptz,
    deleted_by varchar(200),
    CONSTRAINT career_opportunity_id_pk PRIMARY KEY (id),
    CONSTRAINT career_opportunity_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT career_opportunity_version_ck CHECK (version >= 0),
    CONSTRAINT career_opportunity_rule_1_ck CHECK (kind IN ('JOB','INTERNSHIP','INFLUENCER','AFFILIATE')),
    CONSTRAINT career_opportunity_rule_2_ck CHECK (state IN ('DRAFT','PUBLISHED','CLOSED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.career_opportunity TO nigson_experience_app;

CREATE TABLE experience.career_application (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    opportunity_id uuid NOT NULL,
    name varchar(160) NOT NULL,
    email_ciphertext bytea NOT NULL,
    email_hash char(64) NOT NULL,
    answers jsonb NOT NULL,
    form_revision integer NOT NULL,
    cv_file_id uuid,
    state varchar(20) NOT NULL,
    retention_until timestamptz NOT NULL,
    CONSTRAINT career_application_id_pk PRIMARY KEY (id),
    CONSTRAINT career_application_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT career_application_version_ck CHECK (version >= 0),
    CONSTRAINT career_application_opportunity_id_nonnil_ck CHECK (opportunity_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT career_application_cv_file_id_nonnil_ck CHECK (cv_file_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT career_application_rule_1_ck CHECK (form_revision>0),
    CONSTRAINT career_application_rule_2_ck CHECK (state IN ('SUBMITTED','REVIEWED','SHORTLISTED','REJECTED','CLOSED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.career_application TO nigson_experience_app;

CREATE TABLE experience.campaign (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    code varchar(80) NOT NULL,
    title varchar(180) NOT NULL,
    market_id uuid NOT NULL,
    commercial_rule_id uuid,
    display_config jsonb NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    published boolean NOT NULL DEFAULT false,
    CONSTRAINT campaign_id_pk PRIMARY KEY (id),
    CONSTRAINT campaign_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT campaign_version_ck CHECK (version >= 0),
    CONSTRAINT campaign_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT campaign_commercial_rule_id_nonnil_ck CHECK (commercial_rule_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT campaign_rule_1_ck CHECK (ends_at>starts_at)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.campaign TO nigson_experience_app;

CREATE TABLE experience.upload_session (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    owner_party_id uuid,
    token_hash char(64) NOT NULL,
    purpose varchar(30) NOT NULL,
    expires_at timestamptz NOT NULL,
    maximum_bytes bigint NOT NULL,
    claimed_application_id uuid,
    CONSTRAINT upload_session_id_pk PRIMARY KEY (id),
    CONSTRAINT upload_session_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT upload_session_version_ck CHECK (version >= 0),
    CONSTRAINT upload_session_owner_party_id_nonnil_ck CHECK (owner_party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT upload_session_claimed_application_id_nonnil_ck CHECK (claimed_application_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT upload_session_rule_1_ck CHECK (maximum_bytes>0),
    CONSTRAINT upload_session_rule_2_ck CHECK (purpose IN ('CAREER_CV'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON experience.upload_session TO nigson_experience_app;
