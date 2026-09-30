--liquibase formatted sql

--changeset genlink:0016-add-user-token-version
-- Access tokens carry the user's token_version as a "tv" claim. Bumping this
-- column invalidates every access token already issued for the user, which is
-- what makes logout, password change and revoke-all-sessions take effect
-- immediately instead of waiting for the token to expire.
ALTER TABLE users ADD COLUMN IF NOT EXISTS token_version INTEGER NOT NULL DEFAULT 0;

--rollback ALTER TABLE users DROP COLUMN IF EXISTS token_version;
