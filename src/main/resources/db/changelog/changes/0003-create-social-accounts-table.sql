--liquibase formatted sql

--changeset renmakara:0003-create-social-accounts-table
CREATE TABLE IF NOT EXISTS social_accounts (
    social_account_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    provider VARCHAR(30) NOT NULL,
    provider_user_id VARCHAR(180) NOT NULL,
    provider_email VARCHAR(180),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'CampusLinkKH',
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    CONSTRAINT fk_social_accounts_user
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE UNIQUE INDEX IF NOT EXISTS uk_social_provider_user_id
    ON social_accounts(provider, provider_user_id);
CREATE INDEX IF NOT EXISTS idx_social_accounts_user_id ON social_accounts(user_id);

--rollback DROP TABLE IF EXISTS social_accounts;
