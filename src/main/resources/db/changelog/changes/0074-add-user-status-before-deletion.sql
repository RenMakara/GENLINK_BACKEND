--liquibase formatted sql

--changeset genlink:0074-add-user-status-before-deletion
-- Keeps the lifecycle state needed to restore an account after a reversible soft deletion.
ALTER TABLE users ADD COLUMN IF NOT EXISTS status_before_deletion VARCHAR(30);

--rollback ALTER TABLE users DROP COLUMN IF EXISTS status_before_deletion;
