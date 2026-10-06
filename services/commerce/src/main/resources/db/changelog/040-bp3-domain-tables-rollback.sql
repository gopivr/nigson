DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.organization) THEN RAISE EXCEPTION 'Populated table commerce.organization: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.organization_membership) THEN RAISE EXCEPTION 'Populated table commerce.organization_membership: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.account_entitlement) THEN RAISE EXCEPTION 'Populated table commerce.account_entitlement: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.price_assignment) THEN RAISE EXCEPTION 'Populated table commerce.price_assignment: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.wholesale_rule) THEN RAISE EXCEPTION 'Populated table commerce.wholesale_rule: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.quote) THEN RAISE EXCEPTION 'Populated table commerce.quote: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.quote_revision) THEN RAISE EXCEPTION 'Populated table commerce.quote_revision: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM commerce.quote_line) THEN RAISE EXCEPTION 'Populated table commerce.quote_line: use expand/contract migration'; END IF; END $$;

DROP TABLE commerce.quote_line;

DROP TABLE commerce.quote_revision;

DROP TABLE commerce.quote;

DROP TABLE commerce.wholesale_rule;

DROP TABLE commerce.price_assignment;

DROP TABLE commerce.account_entitlement;

DROP TABLE commerce.organization_membership;

DROP TABLE commerce.organization;
