--liquibase formatted sql

--changeset genlink:0061-add-notification-delivery-channels
ALTER TABLE notification_outbox
    ADD COLUMN IF NOT EXISTS channel VARCHAR(20) NOT NULL DEFAULT 'IN_APP';

-- Task 6B created a recipient-only guard.
DROP INDEX IF EXISTS uk_notification_outbox_recipient_dedupe;

CREATE UNIQUE INDEX IF NOT EXISTS uk_notification_outbox_recipient_channel_dedupe
    ON notification_outbox (recipient_user_id, channel, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

--rollback DROP INDEX IF EXISTS uk_notification_outbox_recipient_channel_dedupe;
--rollback CREATE UNIQUE INDEX IF NOT EXISTS uk_notification_outbox_recipient_dedupe ON notification_outbox (recipient_user_id, dedupe_key) WHERE dedupe_key IS NOT NULL;
--rollback ALTER TABLE notification_outbox DROP COLUMN IF EXISTS channel;
