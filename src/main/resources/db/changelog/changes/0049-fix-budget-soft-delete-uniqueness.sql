--liquibase formatted sql

--changeset genlink:0049-fix-budget-soft-delete-uniqueness
-- A deleted budget remains in the table for audit/history.
ALTER TABLE budgets DROP CONSTRAINT IF EXISTS uk_budgets_user_month_year_currency;

CREATE UNIQUE INDEX IF NOT EXISTS uk_budgets_active_user_month_year_currency
    ON budgets (user_id, month, year, currency)
    WHERE deleted_at IS NULL;

--rollback DROP INDEX IF EXISTS uk_budgets_active_user_month_year_currency; ALTER TABLE budgets ADD CONSTRAINT uk_budgets_user_month_year_currency UNIQUE (user_id, month, year, currency);
