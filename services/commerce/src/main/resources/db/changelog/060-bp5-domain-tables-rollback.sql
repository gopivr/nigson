DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.product_recommendation) THEN RAISE EXCEPTION 'Populated table commerce.product_recommendation: use expand/contract migration'; END IF; END $$;

DROP TABLE commerce.product_recommendation;
