--liquibase formatted sql

--changeset genlink:0018-add-event-pricing
-- The Events flow has a Free/Paid toggle and a price.
ALTER TABLE events ADD COLUMN IF NOT EXISTS is_paid BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE events ADD COLUMN IF NOT EXISTS price NUMERIC(12,2);

-- A paid event with no price would take a payment of nothing.
ALTER TABLE events ADD CONSTRAINT chk_events_paid_requires_price
    CHECK (is_paid = FALSE OR (price IS NOT NULL AND price > 0));

CREATE INDEX IF NOT EXISTS idx_events_date_time ON events(date_time);
CREATE INDEX IF NOT EXISTS idx_events_category ON events(category);

--rollback ALTER TABLE events DROP CONSTRAINT IF EXISTS chk_events_paid_requires_price; ALTER TABLE events DROP COLUMN IF EXISTS price; ALTER TABLE events DROP COLUMN IF EXISTS is_paid;
