--liquibase formatted sql

--changeset renmakara:5
--comment: V2 Security Schema Updates

-- 1. Add status column to users and migrate existing active/inactive state
ALTER TABLE users ADD COLUMN IF NOT EXISTS status VARCHAR(20) DEFAULT 'PENDING';

UPDATE users SET status = 'ACTIVE' WHERE active = TRUE;
UPDATE users SET status = 'PENDING' WHERE active = FALSE;

ALTER TABLE users ALTER COLUMN status SET NOT NULL;
ALTER TABLE users DROP COLUMN IF EXISTS active;

-- 2. Create otp_codes table
CREATE TABLE IF NOT EXISTS otp_codes (
    id UUID PRIMARY KEY,
    email VARCHAR(255) NOT NULL,
    code VARCHAR(6) NOT NULL,
    type VARCHAR(50) NOT NULL, -- REGISTER, RESET_PASSWORD, LOGIN_MFA
    expiry_date TIMESTAMP WITH TIME ZONE NOT NULL,
    used BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(255) NOT NULL
);

CREATE INDEX IF NOT EXISTS idx_otp_email_code ON otp_codes(email, code);

-- 3. Migrate existing OTP data from users to otp_codes if exists
INSERT INTO otp_codes (id, email, code, type, expiry_date, used, created_at, created_by, updated_at, updated_by)
SELECT 
    gen_random_uuid(), 
    email, 
    otp_code, 
    'REGISTER', 
    otp_expiry, 
    FALSE, 
    updated_at, 
    updated_by, 
    updated_at, 
    updated_by
FROM users 
WHERE otp_code IS NOT NULL;

-- 4. Clean up OTP fields from users table
ALTER TABLE users DROP COLUMN IF EXISTS otp_code;
ALTER TABLE users DROP COLUMN IF EXISTS otp_expiry;

-- 5. Create security_audit_logs table
CREATE TABLE IF NOT EXISTS security_audit_logs (
    id UUID PRIMARY KEY,
    event_type VARCHAR(100) NOT NULL, -- LOGIN_SUCCESS, LOGIN_FAILED, LOGOUT, PASSWORD_CHANGE, ACCOUNT_BANNED
    email VARCHAR(255),
    ip_address VARCHAR(45),
    device_id VARCHAR(255),
    details TEXT,
    status VARCHAR(50), -- SUCCESS, FAILURE
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(255) NOT NULL
);

-- 6. Create user_profiles table
CREATE TABLE IF NOT EXISTS user_profiles (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    phone_number VARCHAR(20),
    bio TEXT,
    gender VARCHAR(20),
    date_of_birth DATE,
    address TEXT,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(255) NOT NULL
);

-- 7. Create user_settings table
CREATE TABLE IF NOT EXISTS user_settings (
    id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE REFERENCES users(id) ON DELETE CASCADE,
    notifications_enabled BOOLEAN DEFAULT TRUE,
    dark_mode_enabled BOOLEAN DEFAULT FALSE,
    language VARCHAR(10) DEFAULT 'en',
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    created_by VARCHAR(255) NOT NULL,
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL,
    updated_by VARCHAR(255) NOT NULL
);

-- 8. Enhance refresh_tokens table for device tracking
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS device_id VARCHAR(255);
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS ip_address VARCHAR(45);

--rollback ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS device_id;
--rollback ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS ip_address;
--rollback DROP TABLE IF EXISTS user_settings;
--rollback DROP TABLE IF EXISTS user_profiles;
--rollback DROP TABLE IF EXISTS security_audit_logs;
--rollback ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_expiry TIMESTAMP WITH TIME ZONE;
--rollback ALTER TABLE users ADD COLUMN IF NOT EXISTS otp_code VARCHAR(6);
--rollback DROP TABLE IF EXISTS otp_codes;
--rollback ALTER TABLE users ADD COLUMN IF NOT EXISTS active BOOLEAN DEFAULT TRUE;
--rollback UPDATE users SET active = TRUE WHERE status = 'ACTIVE';
--rollback UPDATE users SET active = FALSE WHERE status = 'PENDING' OR status = 'BANNED';
--rollback ALTER TABLE users DROP COLUMN IF EXISTS status;
