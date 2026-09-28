--liquibase formatted sql

--changeset genlink:0082-community-rich-post-fields
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS location VARCHAR(255),
    ADD COLUMN IF NOT EXISTS link_url TEXT,
    ADD COLUMN IF NOT EXISTS link_preview_title VARCHAR(255),
    ADD COLUMN IF NOT EXISTS link_preview_domain VARCHAR(255),
    ADD COLUMN IF NOT EXISTS attached_event_id UUID;

ALTER TABLE posts ADD CONSTRAINT fk_posts_attached_event_id
    FOREIGN KEY (attached_event_id) REFERENCES events(event_id);

CREATE INDEX IF NOT EXISTS idx_posts_attached_event_id
    ON posts(attached_event_id) WHERE attached_event_id IS NOT NULL;

ALTER TABLE polls
    ADD COLUMN IF NOT EXISTS duration_days INTEGER,
    ADD COLUMN IF NOT EXISTS expires_at TIMESTAMP WITH TIME ZONE;

ALTER TABLE polls ADD CONSTRAINT chk_polls_duration_days
    CHECK (duration_days IS NULL OR duration_days BETWEEN 1 AND 30);

--rollback ALTER TABLE polls DROP CONSTRAINT IF EXISTS chk_polls_duration_days; ALTER TABLE polls DROP COLUMN IF EXISTS expires_at; ALTER TABLE polls DROP COLUMN IF EXISTS duration_days; DROP INDEX IF EXISTS idx_posts_attached_event_id; ALTER TABLE posts DROP CONSTRAINT IF EXISTS fk_posts_attached_event_id; ALTER TABLE posts DROP COLUMN IF EXISTS attached_event_id; ALTER TABLE posts DROP COLUMN IF EXISTS link_preview_domain; ALTER TABLE posts DROP COLUMN IF EXISTS link_preview_title; ALTER TABLE posts DROP COLUMN IF EXISTS link_url; ALTER TABLE posts DROP COLUMN IF EXISTS location;
