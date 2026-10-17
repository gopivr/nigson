DO $$ BEGIN IF EXISTS (SELECT 1 FROM experience.lead WHERE email IS NULL) THEN RAISE EXCEPTION 'Cannot restore lead.email NOT NULL while phone-only inbound leads exist'; END IF; END $$;
ALTER TABLE experience.lead ALTER COLUMN email SET NOT NULL;
