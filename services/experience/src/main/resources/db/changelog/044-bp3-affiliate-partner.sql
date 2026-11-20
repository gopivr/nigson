-- AFFILIATE-APPLICATIONS-001: separate affiliate access; preserve prior approvals.
ALTER TABLE experience.distributor_application DROP CONSTRAINT distributor_application_partner_type_ck;
ALTER TABLE experience.distributor_application ADD CONSTRAINT distributor_application_partner_type_ck CHECK (partner_type IN ('DISTRIBUTOR','WHOLESALE','AFFILIATE'));
