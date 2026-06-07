--liquibase formatted sql

--changeset genlink:0011-create-profile-settings-tables
CREATE TABLE IF NOT EXISTS portfolio_items (
    portfolio_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    link TEXT,
    image_url TEXT,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_portfolio_items_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS user_settings (
    settings_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    language VARCHAR(20) NOT NULL,
    theme VARCHAR(30) NOT NULL,
    notification_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    privacy_profile VARCHAR(50),
    allow_messages BOOLEAN NOT NULL DEFAULT FALSE,
    allow_follow_request BOOLEAN NOT NULL DEFAULT FALSE,
    biometric_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_user_settings_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_user_settings_user UNIQUE (user_id)
);

CREATE TABLE IF NOT EXISTS saved_items (
    saved_item_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    item_type VARCHAR(80) NOT NULL,
    item_id UUID NOT NULL,
    saved_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_saved_items_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_saved_items_user_item UNIQUE (user_id, item_type, item_id)
);

--rollback DROP TABLE IF EXISTS saved_items; DROP TABLE IF EXISTS user_settings; DROP TABLE IF EXISTS portfolio_items;
