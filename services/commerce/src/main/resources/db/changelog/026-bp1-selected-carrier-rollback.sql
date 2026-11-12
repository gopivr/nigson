DO $$ BEGIN RAISE EXCEPTION 'Carrier retirement requires a reviewed forward migration; automatic provider reactivation is forbidden'; END $$;
