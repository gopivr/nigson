-- DATA-001: specification 1.1.0; reviewed owner-local DDL.
ALTER TABLE commerce.promotion_rule ADD CONSTRAINT promotion_rule_market_id_fk FOREIGN KEY (market_id) REFERENCES commerce.market (id) ON DELETE RESTRICT;

ALTER TABLE commerce.coupon ADD CONSTRAINT coupon_rule_id_fk FOREIGN KEY (rule_id) REFERENCES commerce.promotion_rule (id) ON DELETE RESTRICT;

ALTER TABLE commerce.coupon ADD CONSTRAINT coupon_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.coupon_redemption ADD CONSTRAINT coupon_redemption_coupon_id_fk FOREIGN KEY (coupon_id) REFERENCES commerce.coupon (id) ON DELETE RESTRICT;

ALTER TABLE commerce.coupon_redemption ADD CONSTRAINT coupon_redemption_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.coupon_redemption ADD CONSTRAINT coupon_redemption_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.reward_entry ADD CONSTRAINT reward_entry_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.reward_entry ADD CONSTRAINT reward_entry_order_id_fk FOREIGN KEY (order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.reward_entry ADD CONSTRAINT reward_entry_rule_id_fk FOREIGN KEY (rule_id) REFERENCES commerce.promotion_rule (id) ON DELETE RESTRICT;

ALTER TABLE commerce.reward_entry ADD CONSTRAINT reward_entry_currency_code_fk FOREIGN KEY (currency_code) REFERENCES commerce.currency (code) ON DELETE RESTRICT;

ALTER TABLE commerce.reward_entry ADD CONSTRAINT reward_entry_reverses_id_fk FOREIGN KEY (reverses_id) REFERENCES commerce.reward_entry (id) ON DELETE RESTRICT;

ALTER TABLE commerce.reward_entry ADD CONSTRAINT reward_entry_coupon_id_fk FOREIGN KEY (coupon_id) REFERENCES commerce.coupon (id) ON DELETE RESTRICT;

ALTER TABLE commerce.referral_code ADD CONSTRAINT referral_code_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.referral_attribution ADD CONSTRAINT referral_attribution_referrer_party_id_fk FOREIGN KEY (referrer_party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.referral_attribution ADD CONSTRAINT referral_attribution_referred_party_id_fk FOREIGN KEY (referred_party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.referral_attribution ADD CONSTRAINT referral_attribution_qualifying_order_id_fk FOREIGN KEY (qualifying_order_id) REFERENCES commerce.sales_order (id) ON DELETE RESTRICT;

ALTER TABLE commerce.referral_attribution ADD CONSTRAINT referral_attribution_rule_id_fk FOREIGN KEY (rule_id) REFERENCES commerce.promotion_rule (id) ON DELETE RESTRICT;

ALTER TABLE commerce.wheel_campaign ADD CONSTRAINT wheel_campaign_rule_id_fk FOREIGN KEY (rule_id) REFERENCES commerce.promotion_rule (id) ON DELETE RESTRICT;

ALTER TABLE commerce.wheel_campaign ADD CONSTRAINT wheel_campaign_market_id_fk FOREIGN KEY (market_id) REFERENCES commerce.market (id) ON DELETE RESTRICT;

ALTER TABLE commerce.wheel_outcome ADD CONSTRAINT wheel_outcome_campaign_id_fk FOREIGN KEY (campaign_id) REFERENCES commerce.wheel_campaign (id) ON DELETE RESTRICT;

ALTER TABLE commerce.wheel_draw ADD CONSTRAINT wheel_draw_campaign_id_fk FOREIGN KEY (campaign_id) REFERENCES commerce.wheel_campaign (id) ON DELETE RESTRICT;

ALTER TABLE commerce.wheel_draw ADD CONSTRAINT wheel_draw_party_id_fk FOREIGN KEY (party_id) REFERENCES commerce.party (id) ON DELETE RESTRICT;

ALTER TABLE commerce.wheel_draw ADD CONSTRAINT wheel_draw_outcome_id_fk FOREIGN KEY (outcome_id) REFERENCES commerce.wheel_outcome (id) ON DELETE RESTRICT;

ALTER TABLE commerce.wheel_draw ADD CONSTRAINT wheel_draw_coupon_id_fk FOREIGN KEY (coupon_id) REFERENCES commerce.coupon (id) ON DELETE RESTRICT;
