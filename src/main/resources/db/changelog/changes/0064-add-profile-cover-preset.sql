--liquibase formatted sql

--changeset genlink:0064-add-profile-cover-preset
-- The client bundles ten approved covers.
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS cover_preset INTEGER NOT NULL DEFAULT 0;

ALTER TABLE users
    DROP CONSTRAINT IF EXISTS ck_users_cover_preset;

ALTER TABLE users
    ADD CONSTRAINT ck_users_cover_preset
        CHECK (cover_preset BETWEEN 0 AND 9);

--rollback ALTER TABLE users DROP CONSTRAINT IF EXISTS ck_users_cover_preset; ALTER TABLE users DROP COLUMN IF EXISTS cover_preset;
