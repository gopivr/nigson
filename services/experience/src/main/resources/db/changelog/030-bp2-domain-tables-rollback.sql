DO $$ BEGIN IF EXISTS (SELECT 1 FROM experience.review) THEN RAISE EXCEPTION 'Populated table experience.review: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.newsletter_subscription) THEN RAISE EXCEPTION 'Populated table experience.newsletter_subscription: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.career_opportunity) THEN RAISE EXCEPTION 'Populated table experience.career_opportunity: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.career_application) THEN RAISE EXCEPTION 'Populated table experience.career_application: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.campaign) THEN RAISE EXCEPTION 'Populated table experience.campaign: use expand/contract migration'; END IF; IF EXISTS (SELECT 1 FROM experience.upload_session) THEN RAISE EXCEPTION 'Populated table experience.upload_session: use expand/contract migration'; END IF; END $$;

DROP TABLE experience.upload_session;

DROP TABLE experience.campaign;

DROP TABLE experience.career_application;

DROP TABLE experience.career_opportunity;

DROP TABLE experience.newsletter_subscription;

DROP TABLE experience.review;
