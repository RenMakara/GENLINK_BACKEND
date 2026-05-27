--liquibase formatted sql

--changeset campuslink:007-make-otp-email-legacy-nullable
--comment: OTP codes now link to users by user_id; keep legacy email column nullable for existing migrations
ALTER TABLE otp_codes ALTER COLUMN email DROP NOT NULL;
DROP INDEX IF EXISTS idx_otp_email_code;

--rollback UPDATE otp_codes oc
--rollback SET email = u.email
--rollback FROM users u
--rollback WHERE oc.user_id = u.id AND oc.email IS NULL;
--rollback ALTER TABLE otp_codes ALTER COLUMN email SET NOT NULL;
--rollback CREATE INDEX IF NOT EXISTS idx_otp_email_code ON otp_codes(email, code);
