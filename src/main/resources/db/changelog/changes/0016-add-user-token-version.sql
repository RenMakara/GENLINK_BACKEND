--liquibase formatted sql

--changeset genlink:0016-add-user-token-version
-- Access tokens carry the user's token_version as a "tv" claim.
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;

--rollback ALTER TABLE users DROP COLUMN IF EXISTS token_version;
