--liquibase formatted sql

--changeset genlink:0037-add-ledger-payment-provider
-- Adds payment-provider details to ledger transactions.
ALTER TABLE demo_transactions ADD COLUMN IF NOT EXISTS provider VARCHAR(40);
ALTER TABLE demo_transactions ADD COLUMN IF NOT EXISTS provider_ref VARCHAR(80);

-- Decouple existing data: historical mock KHQR payments keep their money movement.
UPDATE demo_transactions SET provider = 'MOCK_KHQR' WHERE transaction_type = 'MOCK_KHQR_PAYMENT';
UPDATE demo_transactions SET transaction_type = 'KHQR_PAYMENT' WHERE transaction_type = 'MOCK_KHQR_PAYMENT';

--rollback UPDATE demo_transactions SET transaction_type = 'MOCK_KHQR_PAYMENT' WHERE transaction_type = 'KHQR_PAYMENT' AND provider = 'MOCK_KHQR'; ALTER TABLE demo_transactions DROP COLUMN IF EXISTS provider_ref; ALTER TABLE demo_transactions DROP COLUMN IF EXISTS provider;
