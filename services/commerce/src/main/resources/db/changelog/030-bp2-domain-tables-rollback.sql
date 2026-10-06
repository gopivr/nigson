DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.promotion_rule) THEN RAISE EXCEPTION 'Populated table commerce.promotion_rule: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.coupon) THEN RAISE EXCEPTION 'Populated table commerce.coupon: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.coupon_redemption) THEN RAISE EXCEPTION 'Populated table commerce.coupon_redemption: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.reward_entry) THEN RAISE EXCEPTION 'Populated table commerce.reward_entry: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.referral_code) THEN RAISE EXCEPTION 'Populated table commerce.referral_code: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.referral_attribution) THEN RAISE EXCEPTION 'Populated table commerce.referral_attribution: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.wheel_campaign) THEN RAISE EXCEPTION 'Populated table commerce.wheel_campaign: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.wheel_outcome) THEN RAISE EXCEPTION 'Populated table commerce.wheel_outcome: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.wheel_draw) THEN RAISE EXCEPTION 'Populated table commerce.wheel_draw: use expand/contract migration'; END IF; END $$;

DROP TABLE commerce.wheel_draw;

DROP TABLE commerce.wheel_outcome;

DROP TABLE commerce.wheel_campaign;

DROP TABLE commerce.referral_attribution;

DROP TABLE commerce.referral_code;

DROP TABLE commerce.reward_entry;

DROP TABLE commerce.coupon_redemption;

DROP TABLE commerce.coupon;

DROP TABLE commerce.promotion_rule;
