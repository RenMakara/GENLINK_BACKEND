--liquibase formatted sql

--changeset genlink:0047-fix-domain-correctness-constraints
-- Budgets are independent per currency.
ALTER TABLE budgets DROP CONSTRAINT IF EXISTS uk_budgets_user_month_year;
ALTER TABLE budgets ADD CONSTRAINT uk_budgets_user_month_year_currency
    UNIQUE (user_id, month, year, currency);

-- Retain the earliest historical award for each logical source before enforcing idempotency.
DELETE FROM xp_history
WHERE xp_history_id IN (
    SELECT xp_history_id
    FROM (
        SELECT xp_history_id,
               ROW_NUMBER() OVER (
                   PARTITION BY user_id, source_type, source_id
                   ORDER BY created_at, xp_history_id
               ) AS duplicate_number
        FROM xp_history
        WHERE source_id IS NOT NULL
    ) ranked_awards
    WHERE duplicate_number > 1
);

-- Keep the denormalized user counters consistent with the repaired ledger.
UPDATE users
SET total_xp = CAST(COALESCE((
        SELECT SUM(x.xp_amount) FROM xp_history x WHERE x.user_id = users.user_id
    ), 0) AS INTEGER),
    level = 1 + CAST(COALESCE((
        SELECT SUM(x.xp_amount) FROM xp_history x WHERE x.user_id = users.user_id
    ), 0) / 100 AS INTEGER);

ALTER TABLE xp_history ADD CONSTRAINT uk_xp_history_user_source
    UNIQUE (user_id, source_type, source_id);

--rollback ALTER TABLE xp_history DROP CONSTRAINT IF EXISTS uk_xp_history_user_source; ALTER TABLE budgets DROP CONSTRAINT IF EXISTS uk_budgets_user_month_year_currency; ALTER TABLE budgets ADD CONSTRAINT uk_budgets_user_month_year UNIQUE (user_id, month, year);
