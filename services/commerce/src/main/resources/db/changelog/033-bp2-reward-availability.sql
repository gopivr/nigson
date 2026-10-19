-- COM-REWAR-001: keep held earnings unavailable until the configured eligibility time.
ALTER TABLE commerce.reward_entry ADD COLUMN available_at timestamptz NOT NULL DEFAULT now();
CREATE INDEX reward_entry_available_ix ON commerce.reward_entry (party_id,currency_code,available_at);
