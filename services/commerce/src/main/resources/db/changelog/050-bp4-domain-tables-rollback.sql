DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.abandoned_cart_candidate) THEN RAISE EXCEPTION 'Populated table commerce.abandoned_cart_candidate: use expand/contract migration'; END IF; END $$;

DROP TABLE commerce.abandoned_cart_candidate;
