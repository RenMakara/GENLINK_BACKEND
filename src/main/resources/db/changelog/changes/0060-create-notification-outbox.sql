--liquibase formatted sql

--changeset genlink:0060-create-notification-outbox
CREATE TABLE IF NOT EXISTS notification_outbox (
    outbox_id UUID PRIMARY KEY,
    recipient_user_id UUID NOT NULL,
    template_key VARCHAR(120) NOT NULL,
    type VARCHAR(80) NOT NULL,
    parameters_json TEXT NOT NULL,
    reference_type VARCHAR(40),
    reference_id UUID,
    dedupe_key VARCHAR(160),
    status VARCHAR(20) NOT NULL DEFAULT 'PENDING',
    attempt_count INTEGER NOT NULL DEFAULT 0,
    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_error TEXT,
    processed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_notification_outbox_recipient
        FOREIGN KEY (recipient_user_id) REFERENCES users(user_id),
    CONSTRAINT ck_notification_outbox_attempt_count
        CHECK (attempt_count >= 0)
);

CREATE INDEX IF NOT EXISTS idx_notification_outbox_due
    ON notification_outbox (status, next_attempt_at, created_at);

-- NULL keeps ordinary notifications unrestricted; non-NULL keys are unique per recipient.
CREATE UNIQUE INDEX IF NOT EXISTS uk_notification_outbox_recipient_dedupe
    ON notification_outbox (recipient_user_id, dedupe_key)
    WHERE dedupe_key IS NOT NULL;

--rollback DROP INDEX IF EXISTS uk_notification_outbox_recipient_dedupe;
--rollback DROP INDEX IF EXISTS idx_notification_outbox_due;
--rollback DROP TABLE IF EXISTS notification_outbox;
