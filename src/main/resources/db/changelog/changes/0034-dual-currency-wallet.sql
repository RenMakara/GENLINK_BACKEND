--liquibase formatted sql

--changeset genlink:0034-dual-currency-wallet
-- Model A (backend/docs/FINANCE_LEDGER_PLAN.md, Phase 3b): the demo wallet holds a USD and a
-- KHR balance instead of one balance + a currency label, and each ledger row records its
-- currency. The old single balance was always USD, so it backfills balance_usd; balance_khr
-- starts at 0. Simulation only - no real money in either currency.
ALTER TABLE demo_wallets ADD COLUMN IF NOT EXISTS balance_usd NUMERIC(19,2) NOT NULL DEFAULT 0;
ALTER TABLE demo_wallets ADD COLUMN IF NOT EXISTS balance_khr NUMERIC(19,2) NOT NULL DEFAULT 0;
UPDATE demo_wallets SET balance_usd = COALESCE(balance, 0);
ALTER TABLE demo_wallets DROP COLUMN IF EXISTS balance;
ALTER TABLE demo_wallets DROP COLUMN IF EXISTS currency;

ALTER TABLE demo_transactions ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD';

--rollback ALTER TABLE demo_wallets ADD COLUMN balance NUMERIC(19,2); ALTER TABLE demo_wallets ADD COLUMN currency VARCHAR(10); UPDATE demo_wallets SET balance = balance_usd, currency = 'USD'; ALTER TABLE demo_wallets DROP COLUMN IF EXISTS balance_usd; ALTER TABLE demo_wallets DROP COLUMN IF EXISTS balance_khr; ALTER TABLE demo_transactions DROP COLUMN IF EXISTS currency;
