--liquibase formatted sql

--changeset genlink:0059-add-notification-preferences-and-quiet-hours
ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS notification_quiet_hours_enabled BOOLEAN NOT NULL DEFAULT FALSE;

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS notification_quiet_start TIME;

ALTER TABLE user_settings
    ADD COLUMN IF NOT EXISTS notification_quiet_end TIME;

CREATE TABLE IF NOT EXISTS notification_preferences (
    preference_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    notification_type VARCHAR(80) NOT NULL,
    enabled BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_notification_preferences_user_id
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_notification_preferences_user_type
        UNIQUE (user_id, notification_type)
);

--rollback DROP TABLE IF EXISTS notification_preferences;
--rollback ALTER TABLE user_settings DROP COLUMN IF EXISTS notification_quiet_end;
--rollback ALTER TABLE user_settings DROP COLUMN IF EXISTS notification_quiet_start;
--rollback ALTER TABLE user_settings DROP COLUMN IF EXISTS notification_quiet_hours_enabled;
