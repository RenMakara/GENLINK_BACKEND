--liquibase formatted sql

--changeset campuslink:006-add-user-auth-fields
--comment: Add auth profile fields required by registration and user response DTOs
ALTER TABLE users ADD COLUMN IF NOT EXISTS phone VARCHAR(30);
ALTER TABLE users ADD COLUMN IF NOT EXISTS university VARCHAR(150);
ALTER TABLE users ADD COLUMN IF NOT EXISTS bio TEXT;
CREATE UNIQUE INDEX IF NOT EXISTS uk_users_phone ON users(phone) WHERE phone IS NOT NULL;
CREATE INDEX IF NOT EXISTS idx_users_status ON users(status);
CREATE INDEX IF NOT EXISTS idx_users_email_verified ON users(email_verified);
--rollback DROP INDEX IF EXISTS idx_users_email_verified;
--rollback DROP INDEX IF EXISTS idx_users_status;
--rollback DROP INDEX IF EXISTS uk_users_phone;
--rollback ALTER TABLE users DROP COLUMN IF EXISTS bio;
--rollback ALTER TABLE users DROP COLUMN IF EXISTS university;
--rollback ALTER TABLE users DROP COLUMN IF EXISTS phone;

--changeset campuslink:006-rename-refresh-token-expiry
--comment: Rename refresh token expiry column to match RefreshToken.expiresAt
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'refresh_tokens' AND column_name = 'expiry_date'
ALTER TABLE refresh_tokens RENAME COLUMN expiry_date TO expires_at;
CREATE INDEX IF NOT EXISTS idx_refresh_token_expires_at ON refresh_tokens(expires_at);
--rollback DROP INDEX IF EXISTS idx_refresh_token_expires_at;
--rollback ALTER TABLE refresh_tokens RENAME COLUMN expires_at TO expiry_date;

--changeset campuslink:006-rename-otp-expiry
--comment: Rename OTP expiry column to match OtpCode.expiresAt
--preconditions onFail:MARK_RAN
--precondition-sql-check expectedResult:1 SELECT COUNT(*) FROM information_schema.columns WHERE table_name = 'otp_codes' AND column_name = 'expiry_date'
ALTER TABLE otp_codes RENAME COLUMN expiry_date TO expires_at;
--rollback ALTER TABLE otp_codes RENAME COLUMN expires_at TO expiry_date;

--changeset campuslink:006-link-otp-to-user
--comment: Link OTP codes to users while keeping the legacy email column for migration compatibility
UPDATE otp_codes SET type = 'RESET_PASSWORD' WHERE type = 'FORGOT_PASSWORD';
ALTER TABLE otp_codes ADD COLUMN IF NOT EXISTS user_id UUID;
UPDATE otp_codes oc
SET user_id = u.id
FROM users u
WHERE oc.user_id IS NULL AND oc.email = u.email;
DELETE FROM otp_codes WHERE user_id IS NULL;
ALTER TABLE otp_codes ALTER COLUMN user_id SET NOT NULL;
ALTER TABLE otp_codes ALTER COLUMN used SET NOT NULL;
ALTER TABLE otp_codes
    ADD CONSTRAINT fk_otp_code_user FOREIGN KEY (user_id) REFERENCES users(id) ON DELETE CASCADE;
CREATE INDEX IF NOT EXISTS idx_otp_user_type_code ON otp_codes(user_id, type, code);
--rollback DROP INDEX IF EXISTS idx_otp_user_type_code;
--rollback ALTER TABLE otp_codes DROP CONSTRAINT IF EXISTS fk_otp_code_user;
--rollback ALTER TABLE otp_codes DROP COLUMN IF EXISTS user_id;
--rollback UPDATE otp_codes SET type = 'FORGOT_PASSWORD' WHERE type = 'RESET_PASSWORD';
