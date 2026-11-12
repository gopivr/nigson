-- Retain historic provider identities; new or updated quotes use only the selected carrier.
ALTER TABLE commerce.carrier_quote DROP CONSTRAINT carrier_quote_rule_1_ck;
ALTER TABLE commerce.carrier_quote ADD CONSTRAINT carrier_quote_rule_1_ck CHECK (provider='PANDAGO') NOT VALID;
ALTER TABLE commerce.carrier_quote ADD CONSTRAINT carrier_quote_selected_provider_ck CHECK (provider='PANDAGO') NOT VALID;
