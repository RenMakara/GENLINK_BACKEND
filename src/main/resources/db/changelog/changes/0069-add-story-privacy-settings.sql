--liquibase formatted sql

--changeset genlink:0069-add-story-privacy-settings
ALTER TABLE stories
    ADD COLUMN IF NOT EXISTS visibility VARCHAR(50) NOT NULL DEFAULT 'PUBLIC',
    ADD COLUMN IF NOT EXISTS reply_permission VARCHAR(50) NOT NULL DEFAULT 'EVERYONE',
    ADD COLUMN IF NOT EXISTS allow_reactions BOOLEAN NOT NULL DEFAULT TRUE;

CREATE INDEX IF NOT EXISTS idx_stories_feed_visibility ON stories (user_id, expires_at, visibility);

--rollback ALTER TABLE stories DROP COLUMN IF EXISTS visibility, DROP COLUMN IF EXISTS reply_permission, DROP COLUMN IF EXISTS allow_reactions; DROP INDEX IF EXISTS idx_stories_feed_visibility;
