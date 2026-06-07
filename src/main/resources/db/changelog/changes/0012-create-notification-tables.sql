--liquibase formatted sql

--changeset genlink:0012-create-notification-tables
CREATE TABLE IF NOT EXISTS notification_templates (
    template_id UUID PRIMARY KEY,
    template_key VARCHAR(120) NOT NULL,
    title_en VARCHAR(180) NOT NULL,
    title_km VARCHAR(180),
    message_en TEXT NOT NULL,
    message_km TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT uk_notification_templates_key UNIQUE (template_key)
);

CREATE TABLE IF NOT EXISTS notifications (
    notification_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    template_id UUID,
    title VARCHAR(180) NOT NULL,
    message TEXT NOT NULL,
    language VARCHAR(20) NOT NULL,
    type VARCHAR(80),
    read_status BOOLEAN NOT NULL DEFAULT FALSE,
    read_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_notifications_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_notifications_template_id FOREIGN KEY (template_id) REFERENCES notification_templates(template_id)
);

--rollback DROP TABLE IF EXISTS notifications; DROP TABLE IF EXISTS notification_templates;
