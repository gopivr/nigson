-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE TABLE commerce.organization (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    name varchar(200) NOT NULL,
    business_reference varchar(100),
    country_code char(2) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'PENDING',
    CONSTRAINT organization_id_pk PRIMARY KEY (id),
    CONSTRAINT organization_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT organization_version_ck CHECK (version >= 0),
    CONSTRAINT organization_rule_1_ck CHECK (status IN ('PENDING','ACTIVE','SUSPENDED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.organization TO nigson_commerce_app;

CREATE TABLE commerce.organization_membership (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    organization_id uuid NOT NULL,
    party_id uuid NOT NULL,
    role varchar(20) NOT NULL,
    status varchar(20) NOT NULL,
    CONSTRAINT organization_membership_id_pk PRIMARY KEY (id),
    CONSTRAINT organization_membership_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT organization_membership_version_ck CHECK (version >= 0),
    CONSTRAINT organization_membership_organization_id_nonnil_ck CHECK (organization_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT organization_membership_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT organization_membership_rule_1_ck CHECK (role IN ('OWNER','BUYER')),
    CONSTRAINT organization_membership_rule_2_ck CHECK (status IN ('ACTIVE','SUSPENDED','REVOKED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.organization_membership TO nigson_commerce_app;

CREATE TABLE commerce.account_entitlement (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    organization_id uuid NOT NULL,
    entitlement varchar(40) NOT NULL,
    status varchar(20) NOT NULL,
    source_application_id uuid NOT NULL,
    approval_command_id uuid NOT NULL,
    activated_at timestamptz,
    suspended_at timestamptz,
    CONSTRAINT account_entitlement_id_pk PRIMARY KEY (id),
    CONSTRAINT account_entitlement_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT account_entitlement_version_ck CHECK (version >= 0),
    CONSTRAINT account_entitlement_organization_id_nonnil_ck CHECK (organization_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT account_entitlement_source_application_id_nonnil_ck CHECK (source_application_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT account_entitlement_approval_command_id_nonnil_ck CHECK (approval_command_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT account_entitlement_rule_1_ck CHECK (status IN ('PENDING','ACTIVE','SUSPENDED','REVOKED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.account_entitlement TO nigson_commerce_app;

CREATE TABLE commerce.price_assignment (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    organization_id uuid NOT NULL,
    market_id uuid NOT NULL,
    price_book_id uuid NOT NULL,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT price_assignment_id_pk PRIMARY KEY (id),
    CONSTRAINT price_assignment_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT price_assignment_version_ck CHECK (version >= 0),
    CONSTRAINT price_assignment_organization_id_nonnil_ck CHECK (organization_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT price_assignment_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT price_assignment_price_book_id_nonnil_ck CHECK (price_book_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.price_assignment TO nigson_commerce_app;

CREATE TABLE commerce.wholesale_rule (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    organization_id uuid,
    sku_id uuid,
    market_id uuid NOT NULL,
    minimum_quantity numeric(20,3),
    pack_multiple numeric(20,3),
    minimum_order_amount numeric(19,4),
    rule_revision bigint NOT NULL,
    CONSTRAINT wholesale_rule_id_pk PRIMARY KEY (id),
    CONSTRAINT wholesale_rule_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wholesale_rule_version_ck CHECK (version >= 0),
    CONSTRAINT wholesale_rule_organization_id_nonnil_ck CHECK (organization_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wholesale_rule_sku_id_nonnil_ck CHECK (sku_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wholesale_rule_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wholesale_rule_rule_1_ck CHECK (minimum_quantity IS NULL OR minimum_quantity>0),
    CONSTRAINT wholesale_rule_rule_2_ck CHECK (pack_multiple IS NULL OR pack_multiple>0),
    CONSTRAINT wholesale_rule_rule_3_ck CHECK (minimum_order_amount IS NULL OR minimum_order_amount>=0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.wholesale_rule TO nigson_commerce_app;

CREATE TABLE commerce.quote (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid NOT NULL,
    organization_id uuid,
    market_id uuid NOT NULL,
    source_lead_id uuid NOT NULL,
    status varchar(20) NOT NULL,
    current_revision_id uuid,
    accepted_order_id uuid,
    CONSTRAINT quote_id_pk PRIMARY KEY (id),
    CONSTRAINT quote_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_version_ck CHECK (version >= 0),
    CONSTRAINT quote_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_organization_id_nonnil_ck CHECK (organization_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_source_lead_id_nonnil_ck CHECK (source_lead_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_current_revision_id_nonnil_ck CHECK (current_revision_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_accepted_order_id_nonnil_ck CHECK (accepted_order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_rule_1_ck CHECK (status IN ('DRAFT','OFFERED','ACCEPTED','REJECTED','EXPIRED','CANCELLED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.quote TO nigson_commerce_app;

CREATE TABLE commerce.quote_revision (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    quote_id uuid NOT NULL,
    revision integer NOT NULL,
    currency_code char(3) NOT NULL,
    valid_until timestamptz NOT NULL,
    terms text NOT NULL,
    approval_status varchar(20) NOT NULL,
    approved_by uuid,
    total numeric(19,4) NOT NULL,
    CONSTRAINT quote_revision_id_pk PRIMARY KEY (id),
    CONSTRAINT quote_revision_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_revision_version_ck CHECK (version >= 0),
    CONSTRAINT quote_revision_quote_id_nonnil_ck CHECK (quote_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_revision_approved_by_nonnil_ck CHECK (approved_by <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_revision_rule_1_ck CHECK (revision>0),
    CONSTRAINT quote_revision_rule_2_ck CHECK (total>=0),
    CONSTRAINT quote_revision_rule_3_ck CHECK (approval_status IN ('DRAFT','APPROVED','REJECTED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.quote_revision TO nigson_commerce_app;

CREATE TABLE commerce.quote_line (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    revision_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    quantity numeric(20,3) NOT NULL,
    agreed_unit_amount numeric(19,4) NOT NULL,
    discount_amount numeric(19,4) NOT NULL DEFAULT 0,
    tax_amount numeric(19,4) NOT NULL DEFAULT 0,
    CONSTRAINT quote_line_id_pk PRIMARY KEY (id),
    CONSTRAINT quote_line_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_line_version_ck CHECK (version >= 0),
    CONSTRAINT quote_line_revision_id_nonnil_ck CHECK (revision_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_line_sku_id_nonnil_ck CHECK (sku_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT quote_line_rule_1_ck CHECK (quantity>0),
    CONSTRAINT quote_line_rule_2_ck CHECK (agreed_unit_amount>=0),
    CONSTRAINT quote_line_rule_3_ck CHECK (discount_amount>=0),
    CONSTRAINT quote_line_rule_4_ck CHECK (tax_amount>=0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.quote_line TO nigson_commerce_app;
