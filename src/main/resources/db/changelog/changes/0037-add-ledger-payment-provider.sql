--liquibase formatted sql

--changeset genlink:0037-add-ledger-payment-provider
-- Phase 6 (see backend/docs/FINANCE_LEDGER_PLAN.md): a generic payment-provider seam.
-- The ledger row records WHO processed a payment (provider) and its external reference
-- (provider_ref), so the transaction TYPE can stay provider-neutral (KHQR_PAYMENT, not
-- MOCK_KHQR_PAYMENT). Both columns are nullable: most rows (income, expense, saving, reward,
-- recurring, auto-save) are not payment-provider related. Still simulation-only.
ALTER TABLE demo_transactions ADD COLUMN IF NOT EXISTS provider VARCHAR(40);
ALTER TABLE demo_transactions ADD COLUMN IF NOT EXISTS provider_ref VARCHAR(80);

-- Decouple existing data: historical mock KHQR payments keep their money movement but move the
-- provider identity out of the type and into the provider column.
UPDATE demo_transactions SET provider = 'MOCK_KHQR' WHERE transaction_type = 'MOCK_KHQR_PAYMENT';
UPDATE demo_transactions SET transaction_type = 'KHQR_PAYMENT' WHERE transaction_type = 'MOCK_KHQR_PAYMENT';

--rollback UPDATE demo_transactions SET transaction_type = 'MOCK_KHQR_PAYMENT' WHERE transaction_type = 'KHQR_PAYMENT' AND provider = 'MOCK_KHQR'; ALTER TABLE demo_transactions DROP COLUMN IF EXISTS provider_ref; ALTER TABLE demo_transactions DROP COLUMN IF EXISTS provider;
