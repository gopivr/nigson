DO $$ BEGIN IF EXISTS (SELECT 1 FROM commerce.reward_entry WHERE available_at>now()) THEN RAISE EXCEPTION 'Pending held reward entries: retain availability metadata'; END IF; END $$;
DROP INDEX commerce.reward_entry_available_ix;
ALTER TABLE commerce.reward_entry DROP COLUMN available_at;
