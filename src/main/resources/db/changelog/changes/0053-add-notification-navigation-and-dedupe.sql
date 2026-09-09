--liquibase formatted sql

--changeset genlink:0053-add-notification-navigation-and-dedupe
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS reference_type VARCHAR(40);
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS reference_id UUID;
ALTER TABLE notifications ADD COLUMN IF NOT EXISTS dedupe_key VARCHAR(160);

-- Navigation lookup is useful when Flutter filters or groups notifications by related resource.
CREATE INDEX IF NOT EXISTS idx_notifications_reference
    ON notifications (reference_type, reference_id);

-- A retry-safe key is unique per recipient, while NULL keeps ordinary notifications unrestricted.
CREATE UNIQUE INDEX IF NOT EXISTS uk_notifications_user_dedupe
    ON notifications (user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

--rollback DROP INDEX IF EXISTS uk_notifications_user_dedupe;
--rollback DROP INDEX IF EXISTS idx_notifications_reference;
--rollback ALTER TABLE notifications DROP COLUMN IF EXISTS dedupe_key;
--rollback ALTER TABLE notifications DROP COLUMN IF EXISTS reference_id;
--rollback ALTER TABLE notifications DROP COLUMN IF EXISTS reference_type;
