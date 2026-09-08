--liquibase formatted sql

--changeset genlink:0051-add-event-currency

ALTER TABLE events ADD COLUMN IF NOT EXISTS currency VARCHAR(3);

UPDATE events
SET currency = 'USD'
WHERE is_paid = TRUE AND currency IS NULL;

ALTER TABLE events ADD CONSTRAINT chk_events_currency_value
    CHECK (currency IS NULL OR currency IN ('USD', 'KHR'));

ALTER TABLE events ADD CONSTRAINT chk_events_paid_requires_currency
    CHECK (is_paid = FALSE OR currency IS NOT NULL);

--rollback ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_paid_requires_currency; ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_currency_value; ALTER TABLE events DROP COLUMN IF EXISTS currency;
