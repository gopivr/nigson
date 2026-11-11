ALTER TABLE commerce.carrier_quote
    DROP CONSTRAINT carrier_quote_rule_1_ck,
    ADD CONSTRAINT carrier_quote_rule_1_ck CHECK (provider IN ('FEDEX','PANDAGO'));
