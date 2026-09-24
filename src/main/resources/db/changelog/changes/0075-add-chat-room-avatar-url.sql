--liquibase formatted sql

--changeset genlink:0075-add-chat-room-avatar-url
-- Adds avatar_url to chat_rooms for group profile pictures.
ALTER TABLE chat_rooms ADD COLUMN IF NOT EXISTS avatar_url TEXT;

--rollback ALTER TABLE chat_rooms DROP COLUMN IF EXISTS avatar_url;
