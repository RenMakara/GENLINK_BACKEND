--liquibase formatted sql

--changeset genlink:0065-add-post-video-url
-- Adds video_url column to posts table to support video post attachments.
ALTER TABLE posts
    ADD COLUMN IF NOT EXISTS video_url TEXT;

--rollback ALTER TABLE posts DROP COLUMN IF EXISTS video_url;
