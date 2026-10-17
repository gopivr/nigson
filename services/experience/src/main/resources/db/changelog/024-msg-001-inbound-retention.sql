-- ADR-019: erase sender ciphertext at the explicit inbound retention boundary.
ALTER TABLE experience.inbound_message ALTER COLUMN sender_ciphertext DROP NOT NULL;
