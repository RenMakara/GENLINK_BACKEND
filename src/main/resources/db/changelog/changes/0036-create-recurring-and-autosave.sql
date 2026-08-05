--liquibase formatted sql

--changeset genlink:0036-create-recurring-and-autosave
-- Phase 5 (see backend/docs/FINANCE_LEDGER_PLAN.md): automation over the ledger.
-- recurring_transactions materialise into a real income/expense (and move the wallet) on a
-- schedule; auto_save_rules move money into a saving goal each month. A daily scheduled job
-- (RecurringFinanceJob) drains both by next_run_date.

CREATE TABLE IF NOT EXISTS recurring_transactions (
    recurring_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,           -- INCOME | EXPENSE
    title VARCHAR(160) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    category VARCHAR(80),                -- expense category, or income source
    frequency VARCHAR(20) NOT NULL,      -- DAILY | WEEKLY | MONTHLY
    from_wallet BOOLEAN NOT NULL DEFAULT FALSE,  -- EXPENSE only: also debit the wallet
    next_run_date DATE NOT NULL,
    start_date DATE NOT NULL,
    end_date DATE,                       -- inclusive last date, or open-ended when null
    last_run_date DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_recurring_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- The scheduler scans for due rows; the listing is per user.
CREATE INDEX IF NOT EXISTS idx_recurring_due ON recurring_transactions(active, next_run_date);
CREATE INDEX IF NOT EXISTS idx_recurring_user ON recurring_transactions(user_id);

CREATE TABLE IF NOT EXISTS auto_save_rules (
    rule_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    saving_goal_id UUID NOT NULL,
    type VARCHAR(30) NOT NULL,           -- PERCENT_OF_INCOME | ROUND_UP
    percent NUMERIC(5,2),                -- PERCENT_OF_INCOME only (e.g. 10.00 = 10%)
    currency VARCHAR(3) NOT NULL DEFAULT 'USD',
    next_run_date DATE NOT NULL,
    last_run_date DATE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_autosave_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_autosave_goal_id FOREIGN KEY (saving_goal_id) REFERENCES saving_goals(saving_goal_id)
);

CREATE INDEX IF NOT EXISTS idx_autosave_due ON auto_save_rules(active, next_run_date);
CREATE INDEX IF NOT EXISTS idx_autosave_user ON auto_save_rules(user_id);

--rollback DROP TABLE IF EXISTS auto_save_rules; DROP TABLE IF EXISTS recurring_transactions;
