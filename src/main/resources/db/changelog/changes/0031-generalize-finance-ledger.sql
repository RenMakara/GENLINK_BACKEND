--liquibase formatted sql

--changeset genlink:0031-generalize-finance-ledger
-- demo_transactions is already the money ledger.
ALTER TABLE demo_transactions ADD COLUMN IF NOT EXISTS direction VARCHAR(10);
ALTER TABLE demo_transactions ADD COLUMN IF NOT EXISTS related_type VARCHAR(40);
ALTER TABLE demo_transactions ADD COLUMN IF NOT EXISTS related_id UUID;
ALTER TABLE demo_transactions ADD COLUMN IF NOT EXISTS user_id UUID;

-- Backfill direction from the existing type: deposits and rewards are credits, payments debits.
UPDATE demo_transactions SET direction = 'CREDIT'
    WHERE direction IS NULL AND transaction_type IN ('DEPOSIT', 'REWARD');
UPDATE demo_transactions SET direction = 'DEBIT'
    WHERE direction IS NULL AND transaction_type IN ('MOCK_KHQR_PAYMENT', 'EVENT_PAYMENT');
-- Any unknown legacy type defaults to DEBIT so the NOT NULL below can be set.
UPDATE demo_transactions SET direction = 'DEBIT' WHERE direction IS NULL;

-- Backfill user_id from the owning wallet.
UPDATE demo_transactions t SET user_id = w.user_id
    FROM demo_wallets w WHERE t.wallet_id = w.wallet_id AND t.user_id IS NULL;

ALTER TABLE demo_transactions ALTER COLUMN direction SET NOT NULL;
ALTER TABLE demo_transactions ADD CONSTRAINT fk_demo_transactions_user_id
    FOREIGN KEY (user_id) REFERENCES users(user_id);
CREATE INDEX IF NOT EXISTS idx_demo_transactions_user_id ON demo_transactions(user_id);
CREATE INDEX IF NOT EXISTS idx_demo_transactions_related ON demo_transactions(related_type, related_id);

--rollback ALTER TABLE demo_transactions DROP COLUMN IF EXISTS direction; ALTER TABLE demo_transactions DROP COLUMN IF EXISTS related_type; ALTER TABLE demo_transactions DROP COLUMN IF EXISTS related_id; ALTER TABLE demo_transactions DROP COLUMN IF EXISTS user_id;
