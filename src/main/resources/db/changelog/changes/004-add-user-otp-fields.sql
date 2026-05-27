--liquibase formatted sql

--changeset campuslink-dev:4
--comment: Add OTP and verification fields to users table
ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_code VARCHAR(10);
ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_expiry TIMESTAMP WITH TIME ZONE;
ALTER TABLE users ADD COLUMN IF NOT EXISTS email_verified BOOLEAN NOT NULL DEFAULT FALSE;

-- Update existing users to be email verified if they already exist
UPDATE users SET email_verified = TRUE WHERE email_verified IS NULL;

--rollback ALTER TABLE users DROP COLUMN IF EXISTS email_verified;
--rollback ALTER TABLE users DROP COLUMN IF EXISTS otp_expiry;
--rollback ALTER TABLE users DROP COLUMN IF EXISTS otp_code;
