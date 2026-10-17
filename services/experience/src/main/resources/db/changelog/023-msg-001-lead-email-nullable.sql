-- ADR-019: Experience-owned phone-only verified inbound leads.
ALTER TABLE experience.lead ALTER COLUMN email DROP NOT NULL;
