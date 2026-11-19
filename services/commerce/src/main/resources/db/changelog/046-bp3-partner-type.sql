-- ADR-030: legacy applications/entitlements are distributor records.
ALTER TABLE commerce.account_entitlement ADD COLUMN partner_type varchar(11) NOT NULL DEFAULT 'DISTRIBUTOR' CONSTRAINT account_entitlement_partner_type_ck CHECK (partner_type IN ('DISTRIBUTOR','WHOLESALE'));
