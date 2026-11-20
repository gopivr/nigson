-- AFFILIATE-APPLICATIONS-001: separate affiliate access; preserve prior approvals.
ALTER TABLE commerce.account_entitlement DROP CONSTRAINT account_entitlement_partner_type_ck;
ALTER TABLE commerce.account_entitlement ADD CONSTRAINT account_entitlement_partner_type_ck CHECK (partner_type IN ('DISTRIBUTOR','WHOLESALE','AFFILIATE'));
