--liquibase formatted sql

--changeset genlink:0077-add-chat-shared-content
ALTER TABLE chat_messages ADD COLUMN IF NOT EXISTS shared_content TEXT;

--rollback ALTER TABLE chat_messages DROP COLUMN IF EXISTS shared_content;
