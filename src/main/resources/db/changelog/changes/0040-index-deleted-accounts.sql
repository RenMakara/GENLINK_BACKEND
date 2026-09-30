--liquibase formatted sql

--changeset genlink:0040-index-deleted-accounts
-- Indexes accounts whose deletion grace period has expired.
CREATE INDEX IF NOT EXISTS idx_users_deleted_at_pending_anonymisation
    ON users (deleted_at)
    WHERE account_status = 'DELETED';

--rollback DROP INDEX IF EXISTS idx_users_deleted_at_pending_anonymisation;
