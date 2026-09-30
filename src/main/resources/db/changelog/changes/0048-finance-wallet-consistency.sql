--liquibase formatted sql

--changeset genlink:0048-finance-wallet-consistency
-- Persist whether an expense affected the demo wallet. Existing wallet-paid expenses are
-- reconstructed from their successful debit ledger entry; all other existing expenses remain
-- tracking-only. This is additive and keeps old request/response clients working.
ALTER TABLE expenses ADD COLUMN IF NOT EXISTS paid_from_wallet BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE expenses e
SET paid_from_wallet = TRUE
WHERE EXISTS (
    SELECT 1
    FROM demo_transactions t
    WHERE t.related_type = 'EXPENSE'
      AND t.related_id = e.expense_id
      AND t.direction = 'DEBIT'
      AND t.status = 'SUCCESS'
);

CREATE INDEX IF NOT EXISTS idx_saving_goals_user_currency_status
    ON saving_goals(user_id, currency, status)
    WHERE deleted_at IS NULL;

--rollback DROP INDEX IF EXISTS idx_saving_goals_user_currency_status; ALTER TABLE expenses DROP COLUMN IF EXISTS paid_from_wallet;
