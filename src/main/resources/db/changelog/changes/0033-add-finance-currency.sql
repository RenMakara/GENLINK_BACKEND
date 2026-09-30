--liquibase formatted sql

--changeset genlink:0033-add-finance-currency
-- Adds currency tracking to finance records.
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE incomes ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE budgets ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD';
ALTER TABLE saving_goals ADD COLUMN IF NOT EXISTS currency VARCHAR(3) NOT NULL DEFAULT 'USD';

CREATE INDEX IF NOT EXISTS idx_expenses_user_currency_date ON expenses(user_id, currency, expense_date);
CREATE INDEX IF NOT EXISTS idx_incomes_user_currency_date ON incomes(user_id, currency, income_date);

--rollback ALTER TABLE expenses DROP COLUMN IF EXISTS currency; ALTER TABLE incomes DROP COLUMN IF EXISTS currency; ALTER TABLE budgets DROP COLUMN IF EXISTS currency; ALTER TABLE saving_goals DROP COLUMN IF EXISTS currency;
