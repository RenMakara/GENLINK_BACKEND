--liquibase formatted sql

--changeset campuslink-dev:3
--comment: Update users table for Google Auth
ALTER TABLE users ALTER COLUMN password_hash DROP NOT NULL;
ALTER TABLE users ADD COLUMN IF NOT EXISTS google_id VARCHAR(255);
ALTER TABLE users ADD COLUMN IF NOT EXISTS avatar_url TEXT;
ALTER TABLE users ADD COLUMN IF NOT EXISTS auth_provider VARCHAR(50) DEFAULT 'LOCAL' NOT NULL;

--rollback ALTER TABLE users DROP COLUMN IF EXISTS auth_provider;
--rollback ALTER TABLE users DROP COLUMN IF EXISTS avatar_url;
--rollback ALTER TABLE users DROP COLUMN IF EXISTS google_id;
--rollback ALTER TABLE users ALTER COLUMN password_hash SET NOT NULL;
