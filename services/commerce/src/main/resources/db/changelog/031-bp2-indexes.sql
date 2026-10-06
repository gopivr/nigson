-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE commerce.promotion_rule ADD CONSTRAINT promotion_rule_code_revision_uq UNIQUE (code,revision);

CREATE INDEX promotion_rule_market_id_enabled_starts_at_ix ON commerce.promotion_rule (market_id,enabled,starts_at);

CREATE INDEX promotion_rule_market_id_fk_ix ON commerce.promotion_rule (market_id);

CREATE UNIQUE INDEX coupon_lower_code_uq ON commerce.coupon (lower(code));

CREATE INDEX coupon_party_id_active_ix ON commerce.coupon (party_id,active);

CREATE INDEX coupon_rule_id_fk_ix ON commerce.coupon (rule_id);

CREATE INDEX coupon_party_id_fk_ix ON commerce.coupon (party_id);

ALTER TABLE commerce.coupon_redemption ADD CONSTRAINT coupon_redemption_coupon_id_order_id_uq UNIQUE (coupon_id,order_id);

CREATE INDEX coupon_redemption_coupon_id_party_id_ix ON commerce.coupon_redemption (coupon_id,party_id);

CREATE INDEX coupon_redemption_coupon_id_fk_ix ON commerce.coupon_redemption (coupon_id);

CREATE INDEX coupon_redemption_party_id_fk_ix ON commerce.coupon_redemption (party_id);

CREATE INDEX coupon_redemption_order_id_fk_ix ON commerce.coupon_redemption (order_id);

ALTER TABLE commerce.reward_entry ADD CONSTRAINT reward_entry_business_key_uq UNIQUE (business_key);

CREATE INDEX reward_entry_reverses_id_ix ON commerce.reward_entry (reverses_id);

CREATE INDEX reward_entry_party_id_created_at_ix ON commerce.reward_entry (party_id,created_at);

CREATE INDEX reward_entry_party_id_fk_ix ON commerce.reward_entry (party_id);

CREATE INDEX reward_entry_organization_id_fk_ix ON commerce.reward_entry (organization_id);

CREATE INDEX reward_entry_order_id_fk_ix ON commerce.reward_entry (order_id);

CREATE INDEX reward_entry_rule_id_fk_ix ON commerce.reward_entry (rule_id);

CREATE INDEX reward_entry_currency_code_fk_ix ON commerce.reward_entry (currency_code);

CREATE INDEX reward_entry_reverses_id_fk_ix ON commerce.reward_entry (reverses_id);

CREATE INDEX reward_entry_coupon_id_fk_ix ON commerce.reward_entry (coupon_id);

ALTER TABLE commerce.referral_code ADD CONSTRAINT referral_code_code_uq UNIQUE (code);

ALTER TABLE commerce.referral_code ADD CONSTRAINT referral_code_party_id_uq UNIQUE (party_id);

CREATE INDEX referral_code_party_id_fk_ix ON commerce.referral_code (party_id);

ALTER TABLE commerce.referral_attribution ADD CONSTRAINT referral_attribution_referred_party_id_uq UNIQUE (referred_party_id);

CREATE INDEX referral_attribution_referrer_party_id_fk_ix ON commerce.referral_attribution (referrer_party_id);

CREATE INDEX referral_attribution_referred_party_id_fk_ix ON commerce.referral_attribution (referred_party_id);

CREATE INDEX referral_attribution_qualifying_order_id_fk_ix ON commerce.referral_attribution (qualifying_order_id);

CREATE INDEX referral_attribution_rule_id_fk_ix ON commerce.referral_attribution (rule_id);

ALTER TABLE commerce.wheel_campaign ADD CONSTRAINT wheel_campaign_code_uq UNIQUE (code);

CREATE INDEX wheel_campaign_rule_id_fk_ix ON commerce.wheel_campaign (rule_id);

CREATE INDEX wheel_campaign_market_id_fk_ix ON commerce.wheel_campaign (market_id);

CREATE INDEX wheel_outcome_campaign_id_ix ON commerce.wheel_outcome (campaign_id);

CREATE INDEX wheel_outcome_campaign_id_fk_ix ON commerce.wheel_outcome (campaign_id);

ALTER TABLE commerce.wheel_draw ADD CONSTRAINT wheel_draw_request_key_uq UNIQUE (request_key);

ALTER TABLE commerce.wheel_draw ADD CONSTRAINT wheel_draw_campaign_id_party_id_window_key_draw_number_uq UNIQUE (campaign_id,party_id,window_key,draw_number);

CREATE INDEX wheel_draw_campaign_id_fk_ix ON commerce.wheel_draw (campaign_id);

CREATE INDEX wheel_draw_party_id_fk_ix ON commerce.wheel_draw (party_id);

CREATE INDEX wheel_draw_outcome_id_fk_ix ON commerce.wheel_draw (outcome_id);

CREATE INDEX wheel_draw_coupon_id_fk_ix ON commerce.wheel_draw (coupon_id);
