-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE TABLE commerce.party (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    identity_subject varchar(200) NOT NULL,
    identity_issuer varchar(500) NOT NULL,
    email varchar(254) NOT NULL,
    display_name varchar(160) NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    email_verified boolean NOT NULL DEFAULT false,
    CONSTRAINT party_id_pk PRIMARY KEY (id),
    CONSTRAINT party_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT party_version_ck CHECK (version >= 0),
    CONSTRAINT party_rule_1_ck CHECK (status IN ('ACTIVE','SUSPENDED','ANONYMIZED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.party TO nigson_commerce_app;

CREATE TABLE commerce.customer_profile (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid NOT NULL,
    phone varchar(32),
    locale varchar(16) NOT NULL DEFAULT 'en',
    preferred_currency char(3),
    CONSTRAINT customer_profile_id_pk PRIMARY KEY (id),
    CONSTRAINT customer_profile_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT customer_profile_version_ck CHECK (version >= 0),
    CONSTRAINT customer_profile_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.customer_profile TO nigson_commerce_app;

CREATE TABLE commerce.staff_role_assignment (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid NOT NULL,
    role varchar(30) NOT NULL,
    granted_by uuid NOT NULL,
    revoked_at timestamptz,
    CONSTRAINT staff_role_assignment_id_pk PRIMARY KEY (id),
    CONSTRAINT staff_role_assignment_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT staff_role_assignment_version_ck CHECK (version >= 0),
    CONSTRAINT staff_role_assignment_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT staff_role_assignment_granted_by_nonnil_ck CHECK (granted_by <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT staff_role_assignment_rule_1_ck CHECK (role IN ('ADMIN','CONTENT_EDITOR','CONTENT_PUBLISHER','SALES','OPERATIONS','FINANCE','ANALYST'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.staff_role_assignment TO nigson_commerce_app;

CREATE TABLE commerce.address (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid NOT NULL,
    organization_id uuid,
    label varchar(60),
    recipient varchar(160) NOT NULL,
    phone varchar(32) NOT NULL,
    line1 varchar(200) NOT NULL,
    line2 varchar(200),
    city varchar(100) NOT NULL,
    region varchar(100),
    postal_code varchar(24),
    country_code char(2) NOT NULL,
    is_default boolean NOT NULL DEFAULT false,
    deleted_at timestamptz,
    deleted_by varchar(200),
    CONSTRAINT address_id_pk PRIMARY KEY (id),
    CONSTRAINT address_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT address_version_ck CHECK (version >= 0),
    CONSTRAINT address_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT address_organization_id_nonnil_ck CHECK (organization_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.address TO nigson_commerce_app;

CREATE TABLE commerce.brand (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    name varchar(120) NOT NULL,
    slug varchar(140) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    deleted_at timestamptz,
    deleted_by varchar(200),
    CONSTRAINT brand_id_pk PRIMARY KEY (id),
    CONSTRAINT brand_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT brand_version_ck CHECK (version >= 0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.brand TO nigson_commerce_app;

CREATE TABLE commerce.category (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    parent_id uuid,
    name varchar(120) NOT NULL,
    slug varchar(140) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,
    deleted_at timestamptz,
    deleted_by varchar(200),
    CONSTRAINT category_id_pk PRIMARY KEY (id),
    CONSTRAINT category_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT category_version_ck CHECK (version >= 0),
    CONSTRAINT category_parent_id_nonnil_ck CHECK (parent_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.category TO nigson_commerce_app;

CREATE TABLE commerce.product (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    brand_id uuid,
    division_code varchar(40),
    name varchar(200) NOT NULL,
    slug varchar(220) NOT NULL,
    description text NOT NULL,
    specifications jsonb NOT NULL DEFAULT '{}'::jsonb,
    status varchar(20) NOT NULL DEFAULT 'DRAFT',
    revision bigint NOT NULL DEFAULT 1,
    published_at timestamptz,
    deleted_at timestamptz,
    deleted_by varchar(200),
    CONSTRAINT product_id_pk PRIMARY KEY (id),
    CONSTRAINT product_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT product_version_ck CHECK (version >= 0),
    CONSTRAINT product_brand_id_nonnil_ck CHECK (brand_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT product_rule_1_ck CHECK (status IN ('DRAFT','ACTIVE','ARCHIVED')),
    CONSTRAINT product_rule_2_ck CHECK (jsonb_typeof(specifications)='object'),
    CONSTRAINT product_rule_3_ck CHECK (revision>0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.product TO nigson_commerce_app;

CREATE TABLE commerce.sku (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    product_id uuid NOT NULL,
    code varchar(80) NOT NULL,
    variant_name varchar(160) NOT NULL,
    unit varchar(20) NOT NULL,
    quantity_scale smallint NOT NULL DEFAULT 0,
    pack_size numeric(20,3) NOT NULL DEFAULT 1,
    weight_kg numeric(12,3),
    dimensions jsonb,
    active boolean NOT NULL DEFAULT true,
    carrier_packaging jsonb,
    deleted_at timestamptz,
    deleted_by varchar(200),
    CONSTRAINT sku_id_pk PRIMARY KEY (id),
    CONSTRAINT sku_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT sku_version_ck CHECK (version >= 0),
    CONSTRAINT sku_product_id_nonnil_ck CHECK (product_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT sku_rule_1_ck CHECK (quantity_scale BETWEEN 0 AND 3),
    CONSTRAINT sku_rule_2_ck CHECK (pack_size>0),
    CONSTRAINT sku_rule_3_ck CHECK (weight_kg IS NULL OR weight_kg>=0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.sku TO nigson_commerce_app;

CREATE TABLE commerce.product_category (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    product_id uuid NOT NULL,
    category_id uuid NOT NULL,
    CONSTRAINT product_category_id_pk PRIMARY KEY (id),
    CONSTRAINT product_category_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT product_category_version_ck CHECK (version >= 0),
    CONSTRAINT product_category_product_id_nonnil_ck CHECK (product_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT product_category_category_id_nonnil_ck CHECK (category_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.product_category TO nigson_commerce_app;

CREATE TABLE commerce.product_attribute (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    product_id uuid NOT NULL,
    attribute_key varchar(80) NOT NULL,
    value jsonb NOT NULL,
    schema_version integer NOT NULL DEFAULT 1,
    CONSTRAINT product_attribute_id_pk PRIMARY KEY (id),
    CONSTRAINT product_attribute_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT product_attribute_version_ck CHECK (version >= 0),
    CONSTRAINT product_attribute_product_id_nonnil_ck CHECK (product_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT product_attribute_rule_1_ck CHECK (schema_version>0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.product_attribute TO nigson_commerce_app;

CREATE TABLE commerce.asset_reference (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    product_id uuid,
    sku_id uuid,
    object_key varchar(500) NOT NULL,
    purpose varchar(20) NOT NULL,
    mime_type varchar(100) NOT NULL,
    byte_size bigint NOT NULL,
    checksum_sha256 char(64) NOT NULL,
    status varchar(20) NOT NULL,
    sort_order integer NOT NULL DEFAULT 0,
    classification varchar(20) NOT NULL DEFAULT 'PUBLIC',
    shipment_id uuid,
    CONSTRAINT asset_reference_id_pk PRIMARY KEY (id),
    CONSTRAINT asset_reference_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT asset_reference_version_ck CHECK (version >= 0),
    CONSTRAINT asset_reference_product_id_nonnil_ck CHECK (product_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT asset_reference_sku_id_nonnil_ck CHECK (sku_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT asset_reference_shipment_id_nonnil_ck CHECK (shipment_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT asset_reference_rule_1_ck CHECK (byte_size>0),
    CONSTRAINT asset_reference_rule_2_ck CHECK (purpose IN ('IMAGE','BROCHURE','SHIPPING_LABEL')),
    CONSTRAINT asset_reference_rule_3_ck CHECK (status IN ('UPLOADING','QUARANTINED','CLEAN','PUBLISHED','REJECTED')),
    CONSTRAINT asset_reference_rule_4_ck CHECK (classification IN ('PUBLIC','PRIVATE'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.asset_reference TO nigson_commerce_app;

CREATE TABLE commerce.currency (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    code char(3) NOT NULL,
    minor_unit smallint NOT NULL,
    active boolean NOT NULL DEFAULT false,
    CONSTRAINT currency_id_pk PRIMARY KEY (id),
    CONSTRAINT currency_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT currency_version_ck CHECK (version >= 0),
    CONSTRAINT currency_rule_1_ck CHECK (minor_unit BETWEEN 0 AND 4)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.currency TO nigson_commerce_app;

CREATE TABLE commerce.market (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    code varchar(32) NOT NULL,
    name varchar(100) NOT NULL,
    currency_code char(3) NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    policy_revision bigint NOT NULL DEFAULT 1,
    tax_policy jsonb NOT NULL DEFAULT '{}'::jsonb,
    returns_policy_revision varchar(80),
    retail_price_book_id uuid,
    CONSTRAINT market_id_pk PRIMARY KEY (id),
    CONSTRAINT market_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT market_version_ck CHECK (version >= 0),
    CONSTRAINT market_retail_price_book_id_nonnil_ck CHECK (retail_price_book_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT market_rule_1_ck CHECK (policy_revision>0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.market TO nigson_commerce_app;

CREATE TABLE commerce.market_destination (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    market_id uuid NOT NULL,
    country_code char(2) NOT NULL,
    address_rules jsonb NOT NULL,
    delivery_enabled boolean NOT NULL DEFAULT false,
    CONSTRAINT market_destination_id_pk PRIMARY KEY (id),
    CONSTRAINT market_destination_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT market_destination_version_ck CHECK (version >= 0),
    CONSTRAINT market_destination_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT market_destination_rule_1_ck CHECK (jsonb_typeof(address_rules)='object')
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.market_destination TO nigson_commerce_app;

CREATE TABLE commerce.product_market_eligibility (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    product_id uuid NOT NULL,
    market_id uuid NOT NULL,
    eligible boolean NOT NULL DEFAULT false,
    reason varchar(300),
    CONSTRAINT product_market_eligibility_id_pk PRIMARY KEY (id),
    CONSTRAINT product_market_eligibility_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT product_market_eligibility_version_ck CHECK (version >= 0),
    CONSTRAINT product_market_eligibility_product_id_nonnil_ck CHECK (product_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT product_market_eligibility_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.product_market_eligibility TO nigson_commerce_app;

CREATE TABLE commerce.price_book (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    code varchar(60) NOT NULL,
    currency_code char(3) NOT NULL,
    segment varchar(20) NOT NULL,
    revision bigint NOT NULL,
    effective_from timestamptz NOT NULL,
    effective_until timestamptz,
    active boolean NOT NULL DEFAULT false,
    CONSTRAINT price_book_id_pk PRIMARY KEY (id),
    CONSTRAINT price_book_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT price_book_version_ck CHECK (version >= 0),
    CONSTRAINT price_book_rule_1_ck CHECK (segment IN ('RETAIL','DISTRIBUTOR')),
    CONSTRAINT price_book_rule_2_ck CHECK (revision>0),
    CONSTRAINT price_book_rule_3_ck CHECK (effective_until IS NULL OR effective_until>effective_from)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.price_book TO nigson_commerce_app;

CREATE TABLE commerce.price_entry (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    price_book_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    minimum_quantity numeric(20,3) NOT NULL DEFAULT 1,
    unit_amount numeric(19,4) NOT NULL,
    CONSTRAINT price_entry_id_pk PRIMARY KEY (id),
    CONSTRAINT price_entry_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT price_entry_version_ck CHECK (version >= 0),
    CONSTRAINT price_entry_price_book_id_nonnil_ck CHECK (price_book_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT price_entry_sku_id_nonnil_ck CHECK (sku_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT price_entry_rule_1_ck CHECK (minimum_quantity>0),
    CONSTRAINT price_entry_rule_2_ck CHECK (unit_amount>=0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.price_entry TO nigson_commerce_app;

CREATE TABLE commerce.delivery_rule (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    market_id uuid NOT NULL,
    country_code char(2) NOT NULL,
    region_code varchar(80),
    postal_prefix varchar(24),
    fee numeric(19,4) NOT NULL,
    free_above numeric(19,4),
    priority integer NOT NULL DEFAULT 0,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT delivery_rule_id_pk PRIMARY KEY (id),
    CONSTRAINT delivery_rule_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT delivery_rule_version_ck CHECK (version >= 0),
    CONSTRAINT delivery_rule_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT delivery_rule_rule_1_ck CHECK (fee>=0),
    CONSTRAINT delivery_rule_rule_2_ck CHECK (free_above IS NULL OR free_above>=0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.delivery_rule TO nigson_commerce_app;

CREATE TABLE commerce.cart (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid NOT NULL,
    organization_id uuid,
    market_id uuid NOT NULL,
    status varchar(20) NOT NULL DEFAULT 'ACTIVE',
    last_activity_at timestamptz NOT NULL,
    converted_order_id uuid,
    CONSTRAINT cart_id_pk PRIMARY KEY (id),
    CONSTRAINT cart_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT cart_version_ck CHECK (version >= 0),
    CONSTRAINT cart_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT cart_organization_id_nonnil_ck CHECK (organization_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT cart_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT cart_converted_order_id_nonnil_ck CHECK (converted_order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT cart_rule_1_ck CHECK (status IN ('ACTIVE','CHECKOUT_PENDING','CONVERTED','EXPIRED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.cart TO nigson_commerce_app;

CREATE TABLE commerce.cart_line (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    cart_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    quantity numeric(20,3) NOT NULL,
    CONSTRAINT cart_line_id_pk PRIMARY KEY (id),
    CONSTRAINT cart_line_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT cart_line_version_ck CHECK (version >= 0),
    CONSTRAINT cart_line_cart_id_nonnil_ck CHECK (cart_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT cart_line_sku_id_nonnil_ck CHECK (sku_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT cart_line_rule_1_ck CHECK (quantity>0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.cart_line TO nigson_commerce_app;

CREATE TABLE commerce.checkout_quote (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    cart_id uuid NOT NULL,
    cart_version bigint NOT NULL,
    market_revision bigint NOT NULL,
    address_snapshot jsonb NOT NULL,
    price_snapshot jsonb NOT NULL,
    quote_hash char(64) NOT NULL,
    expires_at timestamptz NOT NULL,
    carrier_quote_id uuid NOT NULL,
    CONSTRAINT checkout_quote_id_pk PRIMARY KEY (id),
    CONSTRAINT checkout_quote_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT checkout_quote_cart_id_nonnil_ck CHECK (cart_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT checkout_quote_carrier_quote_id_nonnil_ck CHECK (carrier_quote_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT checkout_quote_rule_1_ck CHECK (expires_at>created_at)
);

GRANT SELECT, INSERT ON commerce.checkout_quote TO nigson_commerce_app;

CREATE TABLE commerce.sales_order (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    order_number varchar(40) NOT NULL,
    party_id uuid NOT NULL,
    organization_id uuid,
    market_id uuid NOT NULL,
    cart_id uuid,
    status varchar(30) NOT NULL,
    payment_status varchar(30) NOT NULL,
    fulfillment_status varchar(30) NOT NULL,
    currency_code char(3) NOT NULL,
    subtotal numeric(19,4) NOT NULL,
    discount_total numeric(19,4) NOT NULL,
    tax_total numeric(19,4) NOT NULL,
    delivery_total numeric(19,4) NOT NULL,
    grand_total numeric(19,4) NOT NULL,
    refunded_total numeric(19,4) NOT NULL DEFAULT 0,
    address_snapshot jsonb NOT NULL,
    commercial_snapshot jsonb NOT NULL,
    reservation_expires_at timestamptz,
    accepted_quote_revision_id uuid,
    hold_reason varchar(300),
    carrier_quote_id uuid NOT NULL,
    carrier_snapshot jsonb NOT NULL,
    CONSTRAINT sales_order_id_pk PRIMARY KEY (id),
    CONSTRAINT sales_order_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT sales_order_version_ck CHECK (version >= 0),
    CONSTRAINT sales_order_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT sales_order_organization_id_nonnil_ck CHECK (organization_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT sales_order_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT sales_order_cart_id_nonnil_ck CHECK (cart_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT sales_order_accepted_quote_revision_id_nonnil_ck CHECK (accepted_quote_revision_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT sales_order_carrier_quote_id_nonnil_ck CHECK (carrier_quote_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT sales_order_rule_1_ck CHECK (grand_total=subtotal-discount_total+tax_total+delivery_total),
    CONSTRAINT sales_order_rule_2_ck CHECK (subtotal>=0),
    CONSTRAINT sales_order_rule_3_ck CHECK (discount_total>=0),
    CONSTRAINT sales_order_rule_4_ck CHECK (tax_total>=0),
    CONSTRAINT sales_order_rule_5_ck CHECK (delivery_total>=0),
    CONSTRAINT sales_order_rule_6_ck CHECK (grand_total>=0),
    CONSTRAINT sales_order_rule_7_ck CHECK (refunded_total BETWEEN 0 AND grand_total),
    CONSTRAINT sales_order_rule_8_ck CHECK (status IN ('PENDING','PAID','PROCESSING','SHIPPED','DELIVERED','CANCELLED','PAYMENT_HOLD','REFUNDING','REFUNDED')),
    CONSTRAINT sales_order_rule_9_ck CHECK (payment_status IN ('UNPAID','PENDING','PAID','PARTIALLY_REFUNDED','REFUNDED','FAILED')),
    CONSTRAINT sales_order_rule_10_ck CHECK (fulfillment_status IN ('UNFULFILLED','PROCESSING','PARTIALLY_SHIPPED','SHIPPED','DELIVERED','CANCELLED','HOLD'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.sales_order TO nigson_commerce_app;

CREATE TABLE commerce.order_line (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    order_id uuid NOT NULL,
    sku_id uuid NOT NULL,
    sku_code varchar(80) NOT NULL,
    product_name varchar(200) NOT NULL,
    unit varchar(20) NOT NULL,
    quantity numeric(20,3) NOT NULL,
    unit_amount numeric(19,4) NOT NULL,
    discount_amount numeric(19,4) NOT NULL,
    tax_amount numeric(19,4) NOT NULL,
    line_total numeric(19,4) NOT NULL,
    price_book_revision bigint NOT NULL,
    CONSTRAINT order_line_id_pk PRIMARY KEY (id),
    CONSTRAINT order_line_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT order_line_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT order_line_sku_id_nonnil_ck CHECK (sku_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT order_line_rule_1_ck CHECK (quantity>0),
    CONSTRAINT order_line_rule_2_ck CHECK (unit_amount>=0),
    CONSTRAINT order_line_rule_3_ck CHECK (discount_amount>=0),
    CONSTRAINT order_line_rule_4_ck CHECK (tax_amount>=0),
    CONSTRAINT order_line_rule_5_ck CHECK (line_total>=0)
);

GRANT SELECT, INSERT ON commerce.order_line TO nigson_commerce_app;

CREATE TABLE commerce.order_total_component (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    order_id uuid NOT NULL,
    component_type varchar(20) NOT NULL,
    amount numeric(19,4) NOT NULL,
    source_rule_id uuid,
    source_revision bigint,
    metadata jsonb NOT NULL DEFAULT '{}'::jsonb,
    CONSTRAINT order_total_component_id_pk PRIMARY KEY (id),
    CONSTRAINT order_total_component_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT order_total_component_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT order_total_component_source_rule_id_nonnil_ck CHECK (source_rule_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT order_total_component_rule_1_ck CHECK (component_type IN ('SUBTOTAL','DISCOUNT','TAX','DELIVERY','REWARD'))
);

GRANT SELECT, INSERT ON commerce.order_total_component TO nigson_commerce_app;

CREATE TABLE commerce.order_status_history (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    order_id uuid NOT NULL,
    previous_status varchar(30),
    new_status varchar(30) NOT NULL,
    reason varchar(500) NOT NULL,
    source_event_id uuid,
    CONSTRAINT order_status_history_id_pk PRIMARY KEY (id),
    CONSTRAINT order_status_history_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT order_status_history_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT order_status_history_source_event_id_nonnil_ck CHECK (source_event_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT ON commerce.order_status_history TO nigson_commerce_app;

CREATE TABLE commerce.stock_location (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    code varchar(40) NOT NULL,
    name varchar(120) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT stock_location_id_pk PRIMARY KEY (id),
    CONSTRAINT stock_location_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT stock_location_version_ck CHECK (version >= 0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.stock_location TO nigson_commerce_app;

CREATE TABLE commerce.inventory_balance (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    sku_id uuid NOT NULL,
    location_id uuid NOT NULL,
    on_hand numeric(20,3) NOT NULL DEFAULT 0,
    reserved numeric(20,3) NOT NULL DEFAULT 0,
    allocated numeric(20,3) NOT NULL DEFAULT 0,
    CONSTRAINT inventory_balance_id_pk PRIMARY KEY (id),
    CONSTRAINT inventory_balance_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT inventory_balance_version_ck CHECK (version >= 0),
    CONSTRAINT inventory_balance_sku_id_nonnil_ck CHECK (sku_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT inventory_balance_location_id_nonnil_ck CHECK (location_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT inventory_balance_rule_1_ck CHECK (on_hand>=0),
    CONSTRAINT inventory_balance_rule_2_ck CHECK (reserved>=0),
    CONSTRAINT inventory_balance_rule_3_ck CHECK (allocated>=0),
    CONSTRAINT inventory_balance_rule_4_ck CHECK (on_hand>=reserved+allocated)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.inventory_balance TO nigson_commerce_app;

CREATE TABLE commerce.inventory_reservation (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    order_id uuid NOT NULL,
    balance_id uuid NOT NULL,
    quantity numeric(20,3) NOT NULL,
    status varchar(20) NOT NULL,
    expires_at timestamptz NOT NULL,
    CONSTRAINT inventory_reservation_id_pk PRIMARY KEY (id),
    CONSTRAINT inventory_reservation_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT inventory_reservation_version_ck CHECK (version >= 0),
    CONSTRAINT inventory_reservation_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT inventory_reservation_balance_id_nonnil_ck CHECK (balance_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT inventory_reservation_rule_1_ck CHECK (quantity>0),
    CONSTRAINT inventory_reservation_rule_2_ck CHECK (status IN ('RESERVED','ALLOCATED','RELEASED','DISPATCHED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.inventory_reservation TO nigson_commerce_app;

CREATE TABLE commerce.inventory_movement (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    balance_id uuid NOT NULL,
    order_id uuid,
    operation_key varchar(150) NOT NULL,
    movement_type varchar(30) NOT NULL,
    delta_on_hand numeric(20,3) NOT NULL,
    delta_reserved numeric(20,3) NOT NULL,
    delta_allocated numeric(20,3) NOT NULL,
    reason varchar(500) NOT NULL,
    order_line_id uuid,
    return_kind varchar(30),
    receipt_reference varchar(120),
    CONSTRAINT inventory_movement_id_pk PRIMARY KEY (id),
    CONSTRAINT inventory_movement_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT inventory_movement_balance_id_nonnil_ck CHECK (balance_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT inventory_movement_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT inventory_movement_order_line_id_nonnil_ck CHECK (order_line_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT inventory_movement_rule_1_ck CHECK (return_kind IS NULL OR return_kind IN ('PHYSICAL_RETURN','UNSHIPPED_CANCEL')),
    CONSTRAINT inventory_movement_type_ck CHECK (movement_type IN ('RESERVE','SETTLE','RELEASE','DISPATCH','RETURN','ADJUST'))
);

GRANT SELECT, INSERT ON commerce.inventory_movement TO nigson_commerce_app;

CREATE TABLE commerce.payment_attempt (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    order_id uuid NOT NULL,
    provider varchar(40) NOT NULL,
    reference varchar(100) NOT NULL,
    merchant_account varchar(100) NOT NULL,
    expected_amount numeric(19,4) NOT NULL,
    currency_code char(3) NOT NULL,
    state varchar(25) NOT NULL,
    checkout_url varchar(1000),
    initiating_until timestamptz,
    verified_at timestamptz,
    next_reconcile_at timestamptz,
    provider_mode varchar(10) NOT NULL DEFAULT 'MOCK',
    CONSTRAINT payment_attempt_id_pk PRIMARY KEY (id),
    CONSTRAINT payment_attempt_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT payment_attempt_version_ck CHECK (version >= 0),
    CONSTRAINT payment_attempt_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT payment_attempt_rule_1_ck CHECK (expected_amount>=0),
    CONSTRAINT payment_attempt_rule_2_ck CHECK (state IN ('CREATED','INITIATING','PENDING','UNKNOWN','SUCCEEDED','FAILED','CANCELLED')),
    CONSTRAINT payment_attempt_rule_3_ck CHECK (provider_mode IN ('MOCK','TEST','LIVE'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.payment_attempt TO nigson_commerce_app;

CREATE TABLE commerce.payment_transaction (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    attempt_id uuid NOT NULL,
    provider_transaction_id varchar(120) NOT NULL,
    settled_amount numeric(19,4) NOT NULL,
    currency_code char(3) NOT NULL,
    provider_status varchar(40) NOT NULL,
    settled_at timestamptz NOT NULL,
    verification_hash char(64) NOT NULL,
    provider_mode varchar(10) NOT NULL DEFAULT 'MOCK',
    CONSTRAINT payment_transaction_id_pk PRIMARY KEY (id),
    CONSTRAINT payment_transaction_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT payment_transaction_attempt_id_nonnil_ck CHECK (attempt_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT payment_transaction_rule_1_ck CHECK (settled_amount>=0),
    CONSTRAINT payment_transaction_rule_2_ck CHECK (provider_mode IN ('MOCK','TEST','LIVE'))
);

GRANT SELECT, INSERT ON commerce.payment_transaction TO nigson_commerce_app;

CREATE TABLE commerce.webhook_receipt (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    provider varchar(40) NOT NULL,
    merchant_account varchar(100) NOT NULL,
    event_key varchar(200) NOT NULL,
    body_hash char(64) NOT NULL,
    payload_ciphertext bytea NOT NULL,
    verified_signature boolean NOT NULL,
    received_at timestamptz NOT NULL,
    state varchar(20) NOT NULL,
    rejection_code varchar(60),
    CONSTRAINT webhook_receipt_id_pk PRIMARY KEY (id),
    CONSTRAINT webhook_receipt_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT webhook_receipt_version_ck CHECK (version >= 0),
    CONSTRAINT webhook_receipt_rule_1_ck CHECK (state IN ('RECEIVED','APPLIED','REJECTED','RETRY'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.webhook_receipt TO nigson_commerce_app;

CREATE TABLE commerce.refund (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    order_id uuid NOT NULL,
    attempt_id uuid NOT NULL,
    request_key varchar(100) NOT NULL,
    provider_refund_id varchar(120),
    amount numeric(19,4) NOT NULL,
    currency_code char(3) NOT NULL,
    state varchar(20) NOT NULL,
    reason varchar(500) NOT NULL,
    requested_by uuid NOT NULL,
    approved_by uuid,
    provider_reference varchar(120) NOT NULL,
    CONSTRAINT refund_id_pk PRIMARY KEY (id),
    CONSTRAINT refund_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT refund_version_ck CHECK (version >= 0),
    CONSTRAINT refund_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT refund_attempt_id_nonnil_ck CHECK (attempt_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT refund_requested_by_nonnil_ck CHECK (requested_by <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT refund_approved_by_nonnil_ck CHECK (approved_by <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT refund_rule_1_ck CHECK (amount>0),
    CONSTRAINT refund_rule_2_ck CHECK (state IN ('REQUESTED','APPROVED','SUBMITTING','PENDING','UNKNOWN','SUCCEEDED','FAILED','REJECTED'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.refund TO nigson_commerce_app;

CREATE TABLE commerce.refund_line (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    refund_id uuid NOT NULL,
    order_line_id uuid NOT NULL,
    quantity numeric(20,3) NOT NULL,
    amount numeric(19,4) NOT NULL,
    CONSTRAINT refund_line_id_pk PRIMARY KEY (id),
    CONSTRAINT refund_line_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT refund_line_version_ck CHECK (version >= 0),
    CONSTRAINT refund_line_refund_id_nonnil_ck CHECK (refund_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT refund_line_order_line_id_nonnil_ck CHECK (order_line_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT refund_line_rule_1_ck CHECK (quantity>0),
    CONSTRAINT refund_line_rule_2_ck CHECK (amount>=0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.refund_line TO nigson_commerce_app;

CREATE TABLE commerce.shipment (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    order_id uuid NOT NULL,
    provider varchar(40) NOT NULL DEFAULT 'MANUAL',
    provider_reference varchar(120),
    tracking_url varchar(1000),
    status varchar(20) NOT NULL,
    dispatched_at timestamptz,
    delivered_at timestamptz,
    booking_request_id uuid,
    package_snapshot jsonb,
    label_asset_id uuid,
    CONSTRAINT shipment_id_pk PRIMARY KEY (id),
    CONSTRAINT shipment_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT shipment_version_ck CHECK (version >= 0),
    CONSTRAINT shipment_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT shipment_booking_request_id_nonnil_ck CHECK (booking_request_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT shipment_label_asset_id_nonnil_ck CHECK (label_asset_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT shipment_rule_1_ck CHECK (status IN ('CREATED','READY','SHIPPED','DELIVERED','CANCELLED','EXCEPTION'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.shipment TO nigson_commerce_app;

CREATE TABLE commerce.shipment_line (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    shipment_id uuid NOT NULL,
    order_line_id uuid NOT NULL,
    balance_id uuid NOT NULL,
    quantity numeric(20,3) NOT NULL,
    CONSTRAINT shipment_line_id_pk PRIMARY KEY (id),
    CONSTRAINT shipment_line_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT shipment_line_version_ck CHECK (version >= 0),
    CONSTRAINT shipment_line_shipment_id_nonnil_ck CHECK (shipment_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT shipment_line_order_line_id_nonnil_ck CHECK (order_line_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT shipment_line_balance_id_nonnil_ck CHECK (balance_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT shipment_line_rule_1_ck CHECK (quantity>0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.shipment_line TO nigson_commerce_app;

CREATE TABLE commerce.shipment_event (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    shipment_id uuid NOT NULL,
    source varchar(40) NOT NULL,
    event_key varchar(180) NOT NULL,
    status varchar(20) NOT NULL,
    occurred_at timestamptz NOT NULL,
    note varchar(500),
    CONSTRAINT shipment_event_id_pk PRIMARY KEY (id),
    CONSTRAINT shipment_event_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT shipment_event_shipment_id_nonnil_ck CHECK (shipment_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT ON commerce.shipment_event TO nigson_commerce_app;

CREATE TABLE commerce.carrier_quote (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    cart_id uuid NOT NULL,
    cart_version bigint NOT NULL,
    market_id uuid NOT NULL,
    provider varchar(30) NOT NULL,
    mode varchar(10) NOT NULL,
    provider_quote_reference varchar(160),
    service_code varchar(80) NOT NULL,
    currency_code char(3) NOT NULL,
    delivery_amount numeric(19,4) NOT NULL,
    address_hash char(64) NOT NULL,
    package_hash char(64) NOT NULL,
    package_snapshot jsonb NOT NULL,
    provider_snapshot jsonb NOT NULL,
    expires_at timestamptz NOT NULL,
    status varchar(20) NOT NULL,
    source_quote_revision_id uuid,
    CONSTRAINT carrier_quote_id_pk PRIMARY KEY (id),
    CONSTRAINT carrier_quote_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_quote_version_ck CHECK (version >= 0),
    CONSTRAINT carrier_quote_cart_id_nonnil_ck CHECK (cart_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_quote_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_quote_source_quote_revision_id_nonnil_ck CHECK (source_quote_revision_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT carrier_quote_rule_1_ck CHECK (provider='FEDEX'),
    CONSTRAINT carrier_quote_rule_2_ck CHECK (mode IN ('MOCK','SANDBOX','LIVE')),
    CONSTRAINT carrier_quote_rule_3_ck CHECK (status IN ('QUOTED','EXPIRED','CONSUMED')),
    CONSTRAINT carrier_quote_rule_4_ck CHECK (delivery_amount>=0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.carrier_quote TO nigson_commerce_app;
