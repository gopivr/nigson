DROP INDEX commerce.wheel_draw_coupon_id_fk_ix;

DROP INDEX commerce.wheel_draw_outcome_id_fk_ix;

DROP INDEX commerce.wheel_draw_party_id_fk_ix;

DROP INDEX commerce.wheel_draw_campaign_id_fk_ix;

ALTER TABLE commerce.wheel_draw DROP CONSTRAINT wheel_draw_campaign_id_party_id_window_key_draw_number_uq;

ALTER TABLE commerce.wheel_draw DROP CONSTRAINT wheel_draw_request_key_uq;

DROP INDEX commerce.wheel_outcome_campaign_id_fk_ix;

DROP INDEX commerce.wheel_outcome_campaign_id_ix;

DROP INDEX commerce.wheel_campaign_market_id_fk_ix;

DROP INDEX commerce.wheel_campaign_rule_id_fk_ix;

ALTER TABLE commerce.wheel_campaign DROP CONSTRAINT wheel_campaign_code_uq;

DROP INDEX commerce.referral_attribution_rule_id_fk_ix;

DROP INDEX commerce.referral_attribution_qualifying_order_id_fk_ix;

DROP INDEX commerce.referral_attribution_referred_party_id_fk_ix;

DROP INDEX commerce.referral_attribution_referrer_party_id_fk_ix;

ALTER TABLE commerce.referral_attribution DROP CONSTRAINT referral_attribution_referred_party_id_uq;

DROP INDEX commerce.referral_code_party_id_fk_ix;

ALTER TABLE commerce.referral_code DROP CONSTRAINT referral_code_party_id_uq;

ALTER TABLE commerce.referral_code DROP CONSTRAINT referral_code_code_uq;

DROP INDEX commerce.reward_entry_coupon_id_fk_ix;

DROP INDEX commerce.reward_entry_reverses_id_fk_ix;

DROP INDEX commerce.reward_entry_currency_code_fk_ix;

DROP INDEX commerce.reward_entry_rule_id_fk_ix;

DROP INDEX commerce.reward_entry_order_id_fk_ix;

DROP INDEX commerce.reward_entry_organization_id_fk_ix;

DROP INDEX commerce.reward_entry_party_id_fk_ix;

DROP INDEX commerce.reward_entry_party_id_created_at_ix;

DROP INDEX commerce.reward_entry_reverses_id_ix;

ALTER TABLE commerce.reward_entry DROP CONSTRAINT reward_entry_business_key_uq;

DROP INDEX commerce.coupon_redemption_order_id_fk_ix;

DROP INDEX commerce.coupon_redemption_party_id_fk_ix;

DROP INDEX commerce.coupon_redemption_coupon_id_fk_ix;

DROP INDEX commerce.coupon_redemption_coupon_id_party_id_ix;

ALTER TABLE commerce.coupon_redemption DROP CONSTRAINT coupon_redemption_coupon_id_order_id_uq;

DROP INDEX commerce.coupon_party_id_fk_ix;

DROP INDEX commerce.coupon_rule_id_fk_ix;

DROP INDEX commerce.coupon_party_id_active_ix;

DROP INDEX commerce.coupon_lower_code_uq;

DROP INDEX commerce.promotion_rule_market_id_fk_ix;

DROP INDEX commerce.promotion_rule_market_id_enabled_starts_at_ix;

ALTER TABLE commerce.promotion_rule DROP CONSTRAINT promotion_rule_code_revision_uq;
