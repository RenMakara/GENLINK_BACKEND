--liquibase formatted sql

--changeset genlink:0027-index-deleted-accounts
-- Supports the daily anonymisation sweep, which asks for accounts whose deletion
-- grace period has expired: WHERE account_status = 'DELETED' AND deleted_at < ?.
-- Without this it is a full scan of users, run unattended every night and getting
-- slower for the entire life of the product.
--
-- Partial index: only soft-deleted rows are ever matched by that query, and they
-- are a rounding error next to the active user base, so indexing the rest would
-- cost write throughput on every signup for no read benefit.
CREATE INDEX IF NOT EXISTS idx_users_deleted_at_pending_anonymisation
    ON users (deleted_at)
    WHERE account_status = 'DELETED';

--rollback DROP INDEX IF EXISTS idx_users_deleted_at_pending_anonymisation;
