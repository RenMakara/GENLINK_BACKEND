--liquibase formatted sql

--changeset genlink:0063-add-mobile-push-devices
ALTER TABLE notification_channel_preferences
    DROP CONSTRAINT IF EXISTS ck_notification_channel_preferences_channel;

ALTER TABLE notification_channel_preferences
    ADD CONSTRAINT ck_notification_channel_preferences_channel
        CHECK (channel IN ('IN_APP', 'EMAIL', 'PUSH', 'MOBILE_PUSH'));

CREATE TABLE IF NOT EXISTS notification_devices (
    device_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    platform VARCHAR(20) NOT NULL,
    token_hash VARCHAR(64) NOT NULL,
    token_ciphertext TEXT NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    last_seen_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    last_failure_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_notification_devices_user_id
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT ck_notification_devices_platform
        CHECK (platform IN ('ANDROID', 'IOS')),
    CONSTRAINT uk_notification_devices_token_hash
        UNIQUE (token_hash)
);

CREATE INDEX IF NOT EXISTS ix_notification_devices_user_active_seen
    ON notification_devices (user_id, active, last_seen_at DESC);

--rollback DROP TABLE IF EXISTS notification_devices;
--rollback ALTER TABLE notification_channel_preferences DROP CONSTRAINT IF EXISTS ck_notification_channel_preferences_channel;
--rollback ALTER TABLE notification_channel_preferences ADD CONSTRAINT ck_notification_channel_preferences_channel CHECK (channel IN ('IN_APP', 'EMAIL', 'PUSH'));
