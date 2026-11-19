-- ADR-030: legacy applications/entitlements are distributor records.
ALTER TABLE experience.distributor_application ADD COLUMN partner_type varchar(11) NOT NULL DEFAULT 'DISTRIBUTOR' CONSTRAINT distributor_application_partner_type_ck CHECK (partner_type IN ('DISTRIBUTOR','WHOLESALE'));
ALTER TABLE experience.distributor_application ADD COLUMN business_details varchar(4000);
