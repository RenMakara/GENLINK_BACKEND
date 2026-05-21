-- liquibase formatted sql

-- changeset makara:1773246000000-1
ALTER TABLE accounts DROP CONSTRAINT IF EXISTS FK_ACCOUNTS_ON_ACCOUNT_TYPE;

-- changeset makara:1773246000000-2
ALTER TABLE accounts ALTER COLUMN account_type_id DROP NOT NULL;

