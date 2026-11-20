DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.affiliate_visit) THEN RAISE EXCEPTION 'Populated table commerce.affiliate_visit: use expand/contract migration'; END IF; END $$;

ALTER TABLE commerce.affiliate_visit DROP CONSTRAINT affiliate_visit_link_id_fk;

DROP INDEX commerce.affiliate_visit_expires_at_ix;

DROP TABLE commerce.affiliate_visit;
