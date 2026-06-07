--liquibase formatted sql

--changeset renmakara:0004-create-otp-verifications-table
CREATE TABLE IF NOT EXISTS otp_verifications (
    otp_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    email VARCHAR(180),
    otp_hash TEXT NOT NULL,
    purpose VARCHAR(30) NOT NULL,
    expires_at TIMESTAMP NOT NULL,
    verified_at TIMESTAMP,
    attempt_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'CampusLinkKH',
    updated_at TIMESTAMP,
    updated_by VARCHAR(255),
    CONSTRAINT fk_otp_verifications_user
        FOREIGN KEY (user_id) REFERENCES users(user_id) ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS idx_otp_verifications_user_purpose
    ON otp_verifications(user_id, purpose, verified_at);
CREATE INDEX IF NOT EXISTS idx_otp_verifications_expires_at ON otp_verifications(expires_at);

--rollback DROP TABLE IF EXISTS otp_verifications;
