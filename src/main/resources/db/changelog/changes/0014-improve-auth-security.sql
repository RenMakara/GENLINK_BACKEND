--liquibase formatted sql

--changeset genlink:0014-improve-auth-security
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS user_agent TEXT;
ALTER TABLE refresh_tokens ADD COLUMN IF NOT EXISTS last_used_at TIMESTAMP;

ALTER TABLE security_audit_logs ADD COLUMN IF NOT EXISTS user_id UUID;
ALTER TABLE security_audit_logs ADD COLUMN IF NOT EXISTS user_agent TEXT;
ALTER TABLE security_audit_logs ADD COLUMN IF NOT EXISTS success BOOLEAN;
ALTER TABLE security_audit_logs ADD COLUMN IF NOT EXISTS failure_reason VARCHAR(120);

ALTER TABLE security_audit_logs
    ADD CONSTRAINT fk_security_audit_logs_user
    FOREIGN KEY (user_id) REFERENCES users(user_id);

CREATE INDEX IF NOT EXISTS idx_security_audit_logs_user_id ON security_audit_logs(user_id);
CREATE INDEX IF NOT EXISTS idx_security_audit_logs_email ON security_audit_logs(email);
CREATE INDEX IF NOT EXISTS idx_security_audit_logs_event_type ON security_audit_logs(event_type);
CREATE INDEX IF NOT EXISTS idx_security_audit_logs_created_at ON security_audit_logs(created_at);
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_last_used_at ON refresh_tokens(last_used_at);

--rollback DROP INDEX IF EXISTS idx_refresh_tokens_last_used_at;
--rollback DROP INDEX IF EXISTS idx_security_audit_logs_created_at;
--rollback DROP INDEX IF EXISTS idx_security_audit_logs_event_type;
--rollback DROP INDEX IF EXISTS idx_security_audit_logs_email;
--rollback DROP INDEX IF EXISTS idx_security_audit_logs_user_id;
--rollback ALTER TABLE security_audit_logs DROP CONSTRAINT IF EXISTS fk_security_audit_logs_user;
--rollback ALTER TABLE security_audit_logs DROP COLUMN IF EXISTS failure_reason;
--rollback ALTER TABLE security_audit_logs DROP COLUMN IF EXISTS success;
--rollback ALTER TABLE security_audit_logs DROP COLUMN IF EXISTS user_agent;
--rollback ALTER TABLE security_audit_logs DROP COLUMN IF EXISTS user_id;
--rollback ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS last_used_at;
--rollback ALTER TABLE refresh_tokens DROP COLUMN IF EXISTS user_agent;
