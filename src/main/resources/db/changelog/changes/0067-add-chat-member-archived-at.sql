--liquibase formatted sql

--changeset genlink:0067-add-chat-member-archived-at
-- Adds archived_at column to chat_room_members table to support archiving conversations.
ALTER TABLE chat_room_members
    ADD COLUMN IF NOT EXISTS archived_at TIMESTAMP;

--rollback ALTER TABLE chat_room_members DROP COLUMN IF EXISTS archived_at;
