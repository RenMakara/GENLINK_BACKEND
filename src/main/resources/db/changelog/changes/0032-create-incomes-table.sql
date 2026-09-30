--liquibase formatted sql

--changeset genlink:0032-create-incomes-table
-- Finance tracked only expenses, so budgeting was half the picture. incomes gives the other
-- half - allowance, part-time, scholarship - which unlocks cash-flow and savings-rate
-- (see backend/docs/FINANCE_LEDGER_PLAN.md, Phase 1). Mirrors the expenses table. Currency is
-- added across all money tables together in Phase 3; everything is implicitly USD until then.
CREATE TABLE IF NOT EXISTS incomes (
    income_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    amount NUMERIC(19,2) NOT NULL,
    source VARCHAR(80),
    description TEXT,
    income_date DATE NOT NULL,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_incomes_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE INDEX IF NOT EXISTS idx_incomes_user_date ON incomes(user_id, income_date);

--rollback DROP TABLE IF EXISTS incomes;
