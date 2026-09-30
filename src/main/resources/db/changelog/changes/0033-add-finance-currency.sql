--liquibase formatted sql

--changeset genlink:0033-add-finance-currency
-- Dual currency, tracking side (backend/docs/FINANCE_LEDGER_PLAN.md, Phase 3a). Cambodia uses
-- KHR and USD every day, so each money record now carries its currency and cash-flow + budgets
-- report per currency. DEFAULT 'USD' backfills every existing row, so nothing breaks. The demo
-- wallet stays USD-only until Phase 3b adds a KHR balance (Model A).
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE incomes ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE saving_goals ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD';

CREATE INDEX IF NOT EXISTS idx_expenses_user_currency_date ON expenses(user_id, currency, expense_date);
CREATE INDEX IF NOT EXISTS idx_incomes_user_currency_date ON incomes(user_id, currency, income_date);

--rollback ALTER TABLE expenses DROP COLUMN IF EXISTS currency; ALTER TABLE incomes DROP COLUMN IF EXISTS currency; ALTER TABLE budgets DROP COLUMN IF EXISTS currency; ALTER TABLE saving_goals DROP COLUMN IF EXISTS currency;
