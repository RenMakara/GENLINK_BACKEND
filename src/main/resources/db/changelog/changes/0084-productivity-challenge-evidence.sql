--liquibase formatted sql

--changeset genlink:0084-productivity-challenge-evidence
ALTER TABLE tasks ADD COLUMN IF NOT EXISTS completed_at TIMESTAMP;
ALTER TABLE quiz_match_answers ALTER COLUMN option_id DROP NOT NULL;

--rollback ALTER TABLE quiz_match_answers ALTER COLUMN option_id SET NOT NULL; ALTER TABLE tasks DROP COLUMN IF EXISTS completed_at;
