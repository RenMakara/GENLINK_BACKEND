-- liquibase formatted sql

-- changeset campuslink-dev:2
-- comment: Create refresh_tokens table for token management
CREATE TABLE refresh_tokens (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    token VARCHAR(500) NOT NULL UNIQUE,
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(255) NOT NULL,
    CONSTRAINT fk_refresh_token_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE
);

CREATE INDEX idx_refresh_token_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_token_value ON refresh_tokens(token);

-- rollback DROP TABLE refresh_tokens;
