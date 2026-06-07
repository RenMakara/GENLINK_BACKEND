--liquibase formatted sql

--changeset renmakara:0006-add-language-support
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS preferred_language VARCHAR(20) NOT NULL DEFAULT 'ENGLISH';

UPDATE users
SET preferred_language = 'ENGLISH'
WHERE preferred_language IS NULL;

--rollback ALTER TABLE users DROP COLUMN IF EXISTS preferred_language;
