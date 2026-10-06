-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
CREATE TABLE commerce.promotion_rule (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    code varchar(80) NOT NULL,
    revision bigint NOT NULL,
    kind varchar(30) NOT NULL,
    market_id uuid NOT NULL,
    starts_at timestamptz NOT NULL,
    ends_at timestamptz NOT NULL,
    parameters jsonb NOT NULL,
    eligibility jsonb NOT NULL,
    stacking_policy varchar(20) NOT NULL,
    enabled boolean NOT NULL DEFAULT false,
    CONSTRAINT promotion_rule_id_pk PRIMARY KEY (id),
    CONSTRAINT promotion_rule_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT promotion_rule_version_ck CHECK (version >= 0),
    CONSTRAINT promotion_rule_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT promotion_rule_rule_1_ck CHECK (revision>0),
    CONSTRAINT promotion_rule_rule_2_ck CHECK (ends_at>starts_at),
    CONSTRAINT promotion_rule_rule_3_ck CHECK (kind IN ('PERCENT','FIXED','FREE_DELIVERY','SPEND_REWARD','REFERRAL','DEALER')),
    CONSTRAINT promotion_rule_rule_4_ck CHECK (stacking_policy IN ('EXCLUSIVE','STACKABLE'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.promotion_rule TO nigson_commerce_app;

CREATE TABLE commerce.coupon (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    rule_id uuid NOT NULL,
    code varchar(80) NOT NULL,
    party_id uuid,
    maximum_uses integer NOT NULL DEFAULT 1,
    maximum_uses_per_party integer NOT NULL DEFAULT 1,
    reserved_uses integer NOT NULL DEFAULT 0,
    consumed_uses integer NOT NULL DEFAULT 0,
    expires_at timestamptz NOT NULL,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT coupon_id_pk PRIMARY KEY (id),
    CONSTRAINT coupon_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT coupon_version_ck CHECK (version >= 0),
    CONSTRAINT coupon_rule_id_nonnil_ck CHECK (rule_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT coupon_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT coupon_rule_1_ck CHECK (maximum_uses>0),
    CONSTRAINT coupon_rule_2_ck CHECK (maximum_uses_per_party>0),
    CONSTRAINT coupon_rule_3_ck CHECK (reserved_uses>=0),
    CONSTRAINT coupon_rule_4_ck CHECK (consumed_uses>=0),
    CONSTRAINT coupon_rule_5_ck CHECK (reserved_uses+consumed_uses<=maximum_uses)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.coupon TO nigson_commerce_app;

CREATE TABLE commerce.coupon_redemption (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    coupon_id uuid NOT NULL,
    party_id uuid NOT NULL,
    order_id uuid NOT NULL,
    state varchar(20) NOT NULL,
    amount numeric(19,4) NOT NULL,
    expires_at timestamptz,
    CONSTRAINT coupon_redemption_id_pk PRIMARY KEY (id),
    CONSTRAINT coupon_redemption_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT coupon_redemption_version_ck CHECK (version >= 0),
    CONSTRAINT coupon_redemption_coupon_id_nonnil_ck CHECK (coupon_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT coupon_redemption_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT coupon_redemption_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT coupon_redemption_rule_1_ck CHECK (state IN ('RESERVED','CONSUMED','RELEASED','REVERSED')),
    CONSTRAINT coupon_redemption_rule_2_ck CHECK (amount>=0)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.coupon_redemption TO nigson_commerce_app;

CREATE TABLE commerce.reward_entry (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    party_id uuid NOT NULL,
    organization_id uuid,
    order_id uuid,
    rule_id uuid NOT NULL,
    kind varchar(20) NOT NULL,
    amount numeric(19,4) NOT NULL,
    currency_code char(3) NOT NULL,
    business_key varchar(180) NOT NULL,
    reverses_id uuid,
    expires_at timestamptz,
    coupon_id uuid,
    CONSTRAINT reward_entry_id_pk PRIMARY KEY (id),
    CONSTRAINT reward_entry_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT reward_entry_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT reward_entry_organization_id_nonnil_ck CHECK (organization_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT reward_entry_order_id_nonnil_ck CHECK (order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT reward_entry_rule_id_nonnil_ck CHECK (rule_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT reward_entry_reverses_id_nonnil_ck CHECK (reverses_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT reward_entry_coupon_id_nonnil_ck CHECK (coupon_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT reward_entry_rule_1_ck CHECK (kind IN ('EARN','REDEEM','EXPIRE','REVERSE','DEALER_ISSUE')),
    CONSTRAINT reward_entry_rule_2_ck CHECK (amount>=0)
);

GRANT SELECT, INSERT ON commerce.reward_entry TO nigson_commerce_app;

CREATE TABLE commerce.referral_code (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    party_id uuid NOT NULL,
    code varchar(60) NOT NULL,
    active boolean NOT NULL DEFAULT true,
    CONSTRAINT referral_code_id_pk PRIMARY KEY (id),
    CONSTRAINT referral_code_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT referral_code_version_ck CHECK (version >= 0),
    CONSTRAINT referral_code_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.referral_code TO nigson_commerce_app;

CREATE TABLE commerce.referral_attribution (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    referrer_party_id uuid NOT NULL,
    referred_party_id uuid NOT NULL,
    qualifying_order_id uuid,
    rule_id uuid NOT NULL,
    state varchar(20) NOT NULL,
    qualify_after timestamptz,
    CONSTRAINT referral_attribution_id_pk PRIMARY KEY (id),
    CONSTRAINT referral_attribution_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT referral_attribution_version_ck CHECK (version >= 0),
    CONSTRAINT referral_attribution_referrer_party_id_nonnil_ck CHECK (referrer_party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT referral_attribution_referred_party_id_nonnil_ck CHECK (referred_party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT referral_attribution_qualifying_order_id_nonnil_ck CHECK (qualifying_order_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT referral_attribution_rule_id_nonnil_ck CHECK (rule_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT referral_attribution_rule_1_ck CHECK (referrer_party_id<>referred_party_id),
    CONSTRAINT referral_attribution_rule_2_ck CHECK (state IN ('ATTRIBUTED','PENDING','QUALIFIED','REVERSED','INELIGIBLE'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.referral_attribution TO nigson_commerce_app;

CREATE TABLE commerce.wheel_campaign (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    rule_id uuid NOT NULL,
    market_id uuid NOT NULL,
    code varchar(80) NOT NULL,
    daily_limit integer NOT NULL DEFAULT 1,
    reward_budget numeric(19,4) NOT NULL,
    reserved_budget numeric(19,4) NOT NULL DEFAULT 0,
    spent_budget numeric(19,4) NOT NULL DEFAULT 0,
    enabled boolean NOT NULL DEFAULT false,
    CONSTRAINT wheel_campaign_id_pk PRIMARY KEY (id),
    CONSTRAINT wheel_campaign_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wheel_campaign_version_ck CHECK (version >= 0),
    CONSTRAINT wheel_campaign_rule_id_nonnil_ck CHECK (rule_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wheel_campaign_market_id_nonnil_ck CHECK (market_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wheel_campaign_rule_1_ck CHECK (daily_limit>0),
    CONSTRAINT wheel_campaign_rule_2_ck CHECK (reward_budget>=0),
    CONSTRAINT wheel_campaign_rule_3_ck CHECK (reserved_budget>=0),
    CONSTRAINT wheel_campaign_rule_4_ck CHECK (spent_budget>=0),
    CONSTRAINT wheel_campaign_rule_5_ck CHECK (reserved_budget+spent_budget<=reward_budget)
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.wheel_campaign TO nigson_commerce_app;

CREATE TABLE commerce.wheel_outcome (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    updated_at timestamptz NOT NULL DEFAULT now(),
    updated_by varchar(200) NOT NULL,
    version bigint NOT NULL DEFAULT 0,
    campaign_id uuid NOT NULL,
    label varchar(100) NOT NULL,
    kind varchar(20) NOT NULL,
    weight integer NOT NULL,
    benefit jsonb NOT NULL,
    CONSTRAINT wheel_outcome_id_pk PRIMARY KEY (id),
    CONSTRAINT wheel_outcome_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wheel_outcome_version_ck CHECK (version >= 0),
    CONSTRAINT wheel_outcome_campaign_id_nonnil_ck CHECK (campaign_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wheel_outcome_rule_1_ck CHECK (weight>0),
    CONSTRAINT wheel_outcome_rule_2_ck CHECK (kind IN ('COUPON','FREE_DELIVERY','RETRY','NONE'))
);

GRANT SELECT, INSERT, UPDATE, DELETE ON commerce.wheel_outcome TO nigson_commerce_app;

CREATE TABLE commerce.wheel_draw (
    id uuid NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now(),
    created_by varchar(200) NOT NULL,
    campaign_id uuid NOT NULL,
    party_id uuid NOT NULL,
    outcome_id uuid NOT NULL,
    window_key date NOT NULL,
    draw_number integer NOT NULL,
    coupon_id uuid,
    request_key varchar(100) NOT NULL,
    CONSTRAINT wheel_draw_id_pk PRIMARY KEY (id),
    CONSTRAINT wheel_draw_id_nonnil_ck CHECK (id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wheel_draw_campaign_id_nonnil_ck CHECK (campaign_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wheel_draw_party_id_nonnil_ck CHECK (party_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wheel_draw_outcome_id_nonnil_ck CHECK (outcome_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wheel_draw_coupon_id_nonnil_ck CHECK (coupon_id <> '00000000-0000-0000-0000-000000000000'::uuid),
    CONSTRAINT wheel_draw_rule_1_ck CHECK (draw_number>0)
);

GRANT SELECT, INSERT ON commerce.wheel_draw TO nigson_commerce_app;
