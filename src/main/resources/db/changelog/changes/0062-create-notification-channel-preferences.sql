--liquibase formatted sql

--changeset genlink:0062-create-notification-channel-preferences
CREATE TABLE IF NOT EXISTS notification_channel_preferences (
    preference_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    channel VARCHAR(20) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_notification_channel_preferences_user_id
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT ck_notification_channel_preferences_channel
        CHECK (channel IN ('IN_APP', 'EMAIL', 'PUSH')),
    CONSTRAINT uk_notification_channel_preferences_user_channel
        UNIQUE (user_id, channel)
);

--rollback DROP TABLE IF EXISTS notification_channel_preferences;
