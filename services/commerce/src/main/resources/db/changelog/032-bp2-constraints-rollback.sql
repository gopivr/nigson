ALTER TABLE commerce.wheel_draw DROP CONSTRAINT wheel_draw_coupon_id_fk;

ALTER TABLE commerce.wheel_draw DROP CONSTRAINT wheel_draw_outcome_id_fk;

ALTER TABLE commerce.wheel_draw DROP CONSTRAINT wheel_draw_party_id_fk;

ALTER TABLE commerce.wheel_draw DROP CONSTRAINT wheel_draw_campaign_id_fk;

ALTER TABLE commerce.wheel_outcome DROP CONSTRAINT wheel_outcome_campaign_id_fk;

ALTER TABLE commerce.wheel_campaign DROP CONSTRAINT wheel_campaign_market_id_fk;

ALTER TABLE commerce.wheel_campaign DROP CONSTRAINT wheel_campaign_rule_id_fk;

ALTER TABLE commerce.referral_attribution DROP CONSTRAINT referral_attribution_rule_id_fk;

ALTER TABLE commerce.referral_attribution DROP CONSTRAINT referral_attribution_qualifying_order_id_fk;

ALTER TABLE commerce.referral_attribution DROP CONSTRAINT referral_attribution_referred_party_id_fk;

ALTER TABLE commerce.referral_attribution DROP CONSTRAINT referral_attribution_referrer_party_id_fk;

ALTER TABLE commerce.referral_code DROP CONSTRAINT referral_code_party_id_fk;

ALTER TABLE commerce.reward_entry DROP CONSTRAINT reward_entry_coupon_id_fk;

ALTER TABLE commerce.reward_entry DROP CONSTRAINT reward_entry_reverses_id_fk;

ALTER TABLE commerce.reward_entry DROP CONSTRAINT reward_entry_currency_code_fk;

ALTER TABLE commerce.reward_entry DROP CONSTRAINT reward_entry_rule_id_fk;

ALTER TABLE commerce.reward_entry DROP CONSTRAINT reward_entry_order_id_fk;

ALTER TABLE commerce.reward_entry DROP CONSTRAINT reward_entry_party_id_fk;

ALTER TABLE commerce.coupon_redemption DROP CONSTRAINT coupon_redemption_order_id_fk;

ALTER TABLE commerce.coupon_redemption DROP CONSTRAINT coupon_redemption_party_id_fk;

ALTER TABLE commerce.coupon_redemption DROP CONSTRAINT coupon_redemption_coupon_id_fk;

ALTER TABLE commerce.coupon DROP CONSTRAINT coupon_party_id_fk;

ALTER TABLE commerce.coupon DROP CONSTRAINT coupon_rule_id_fk;

ALTER TABLE commerce.promotion_rule DROP CONSTRAINT promotion_rule_market_id_fk;
