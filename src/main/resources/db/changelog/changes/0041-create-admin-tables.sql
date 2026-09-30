--liquibase formatted sql

--changeset genlink:0041-create-admin-tables
-- Admin Dashboard schema.

-- 1. admin_roles - the seven staff roles, plus any added later.
CREATE TABLE IF NOT EXISTS admin_roles (
    admin_role_id UUID PRIMARY KEY,
    code VARCHAR(40) NOT NULL UNIQUE,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(255),
    is_system BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH'
);

-- 2. role_permissions - the permission matrix.
CREATE TABLE IF NOT EXISTS role_permissions (
    role_permission_id UUID PRIMARY KEY,
    admin_role_id UUID NOT NULL,
    permission_code VARCHAR(64) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_role_permissions_admin_role_id
        FOREIGN KEY (admin_role_id) REFERENCES admin_roles(admin_role_id) ON DELETE CASCADE,
    CONSTRAINT uq_role_permissions_role_code UNIQUE (admin_role_id, permission_code)
);

-- 3. admin_users - which users are staff.
CREATE TABLE IF NOT EXISTS admin_users (
    admin_user_id UUID PRIMARY KEY,
    user_id UUID NOT NULL UNIQUE,
    admin_role_id UUID NOT NULL,
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    totp_secret TEXT,
    totp_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    totp_confirmed_at TIMESTAMP,
    recovery_codes JSONB,
    last_login_at TIMESTAMP,
    last_login_ip VARCHAR(45),
    invited_by UUID,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_admin_users_user_id
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_admin_users_admin_role_id
        FOREIGN KEY (admin_role_id) REFERENCES admin_roles(admin_role_id),
    CONSTRAINT fk_admin_users_invited_by
        FOREIGN KEY (invited_by) REFERENCES admin_users(admin_user_id)
);

-- Sign-in resolves the admin profile by user on every request.
CREATE INDEX IF NOT EXISTS idx_admin_users_active
    ON admin_users (user_id) WHERE is_active = TRUE AND deleted_at IS NULL;

-- 4. admin_audit_logs - every privileged action, successful or not.
CREATE TABLE IF NOT EXISTS admin_audit_logs (
    admin_audit_log_id UUID PRIMARY KEY,
    admin_user_id UUID,
    action VARCHAR(80) NOT NULL,
    target_type VARCHAR(40),
    target_id UUID,
    before_json JSONB,
    after_json JSONB,
    reason TEXT,
    ip_address VARCHAR(45),
    user_agent TEXT,
    request_id VARCHAR(64),
    batch_id UUID,
    status VARCHAR(20) NOT NULL DEFAULT 'SUCCESS',
    error_code VARCHAR(60),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_admin_audit_logs_admin_user_id
        FOREIGN KEY (admin_user_id) REFERENCES admin_users(admin_user_id)
);

-- "What did this admin do?" - the audit viewer's default filter.
CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_admin_created
    ON admin_audit_logs (admin_user_id, created_at DESC);
-- "What was done to this user/post?" - reached from a user's detail page.
CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_target
    ON admin_audit_logs (target_type, target_id);
-- The dashboard's recent-activity panel and the unfiltered log, newest first.
CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_created
    ON admin_audit_logs (created_at DESC);
-- Stores one audit row per affected user in a bulk operation.
CREATE INDEX IF NOT EXISTS idx_admin_audit_logs_batch
    ON admin_audit_logs (batch_id) WHERE batch_id IS NOT NULL;

-- 5. user_sanctions - warnings, suspensions and bans, in one timeline.
CREATE TABLE IF NOT EXISTS user_sanctions (
    user_sanction_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    admin_user_id UUID NOT NULL,
    type VARCHAR(20) NOT NULL,
    severity VARCHAR(20),
    reason TEXT NOT NULL,
    message_en TEXT,
    message_km TEXT,
    previous_status VARCHAR(30) NOT NULL,
    starts_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMP,
    acknowledged_at TIMESTAMP,
    lifted_at TIMESTAMP,
    lifted_by UUID,
    lift_reason TEXT,
    related_type VARCHAR(40),
    related_id UUID,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_user_sanctions_user_id
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_user_sanctions_admin_user_id
        FOREIGN KEY (admin_user_id) REFERENCES admin_users(admin_user_id),
    CONSTRAINT fk_user_sanctions_lifted_by
        FOREIGN KEY (lifted_by) REFERENCES admin_users(admin_user_id)
);

-- The daily suspension-expiry job asks for exactly this: sanctions still in force whose time is up.
CREATE INDEX IF NOT EXISTS idx_user_sanctions_pending_expiry
    ON user_sanctions (expires_at)
    WHERE lifted_at IS NULL AND expires_at IS NOT NULL;
-- A user's enforcement timeline, newest first.
CREATE INDEX IF NOT EXISTS idx_user_sanctions_user_created
    ON user_sanctions (user_id, created_at DESC);

-- 6. admin_user_notes - internal staff notes about a user.
CREATE TABLE IF NOT EXISTS admin_user_notes (
    admin_user_note_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    admin_user_id UUID NOT NULL,
    note TEXT NOT NULL,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_admin_user_notes_user_id
        FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_admin_user_notes_admin_user_id
        FOREIGN KEY (admin_user_id) REFERENCES admin_users(admin_user_id)
);

CREATE INDEX IF NOT EXISTS idx_admin_user_notes_user_created
    ON admin_user_notes (user_id, created_at DESC);

-- 7. Indexes on existing tables, for admin queries only.

-- The user list's default sort (newest first) combined with its most common filter.
CREATE INDEX IF NOT EXISTS idx_users_created_at_status
    ON users (created_at DESC, account_status);
-- University analytics and the filter bar's typeahead. Partial: the column is optional
-- and null rows are never matched by either query.
CREATE INDEX IF NOT EXISTS idx_users_university
    ON users (university_name) WHERE university_name IS NOT NULL;
-- The user list's "last active" column, and the DAU/MAU estimate.
CREATE INDEX IF NOT EXISTS idx_refresh_tokens_last_used
    ON refresh_tokens (last_used_at DESC);

--rollback DROP INDEX IF EXISTS idx_refresh_tokens_last_used;
--rollback DROP INDEX IF EXISTS idx_users_university;
--rollback DROP INDEX IF EXISTS idx_users_created_at_status;
--rollback DROP TABLE IF EXISTS admin_user_notes;
--rollback DROP TABLE IF EXISTS user_sanctions;
--rollback DROP TABLE IF EXISTS admin_audit_logs;
--rollback DROP TABLE IF EXISTS admin_users;
--rollback DROP TABLE IF EXISTS role_permissions;
--rollback DROP TABLE IF EXISTS admin_roles;


--changeset genlink:0041-seed-admin-roles
-- The seven staff roles and the permission matrix, transcribed from docs/admin-dashboard.md §5.2 and §5.4.

INSERT INTO admin_roles (admin_role_id, code, name, description, is_system) VALUES
    (gen_random_uuid(), 'SUPER_ADMIN', 'Super Admin',
        'Owner. Manages admins and system settings. Implicitly holds every permission, in code.', TRUE),
    (gen_random_uuid(), 'ADMIN', 'Admin',
        'Runs the platform day to day. Everything except RBAC and system settings.', TRUE),
    (gen_random_uuid(), 'MODERATOR', 'Moderator',
        'Content and behaviour enforcement. Content moderation plus user sanctions.', TRUE),
    (gen_random_uuid(), 'SUPPORT', 'Support',
        'Unblocks users; cannot punish them. Read and account-recovery actions only.', TRUE),
    (gen_random_uuid(), 'CONTENT_MANAGER', 'Content Manager',
        'Owns published content and communications, including Khmer localisation.', TRUE),
    (gen_random_uuid(), 'FINANCE_ADMIN', 'Finance Admin',
        'Money oversight. Finance read-only and reconciliation.', TRUE),
    (gen_random_uuid(), 'ANALYTICS_VIEWER', 'Analytics Viewer',
        'Stakeholder visibility. Read-only dashboards, nothing else.', TRUE)
ON CONFLICT (code) DO NOTHING;

-- Joined on role code rather than a hard-coded UUID, so the seed does not have to invent and then remember ids.
INSERT INTO role_permissions (role_permission_id, admin_role_id, permission_code)
SELECT gen_random_uuid(), r.admin_role_id, g.permission_code
FROM (VALUES
    -- ADMIN (37) - all 40 keys except user:export:pii, rbac:manage, settings:manage.
    ('ADMIN', 'dashboard:view'),
    ('ADMIN', 'user:read'),
    ('ADMIN', 'user:update'),
    ('ADMIN', 'user:verify_email'),
    ('ADMIN', 'user:reset_password'),
    ('ADMIN', 'user:force_logout'),
    ('ADMIN', 'user:warn'),
    ('ADMIN', 'user:note:write'),
    ('ADMIN', 'user:suspend'),
    ('ADMIN', 'user:ban'),
    ('ADMIN', 'user:unban'),
    ('ADMIN', 'user:delete'),
    ('ADMIN', 'user:export'),
    ('ADMIN', 'xp:grant'),
    ('ADMIN', 'xp:revoke'),
    ('ADMIN', 'badge:assign'),
    ('ADMIN', 'gamification:manage'),
    ('ADMIN', 'community:moderate'),
    ('ADMIN', 'forum:moderate'),
    ('ADMIN', 'story:moderate'),
    ('ADMIN', 'event:read'),
    ('ADMIN', 'event:manage'),
    ('ADMIN', 'opportunity:manage'),
    ('ADMIN', 'media:read'),
    ('ADMIN', 'media:delete'),
    ('ADMIN', 'notification:send'),
    ('ADMIN', 'notification:template:manage'),
    ('ADMIN', 'report:read'),
    ('ADMIN', 'report:resolve'),
    ('ADMIN', 'finance:read'),
    ('ADMIN', 'finance:ledger:read'),
    ('ADMIN', 'analytics:view'),
    ('ADMIN', 'analytics:export'),
    ('ADMIN', 'localization:manage'),
    ('ADMIN', 'rbac:read'),
    ('ADMIN', 'audit:read'),
    ('ADMIN', 'settings:read'),

    -- MODERATOR (18).
    ('MODERATOR', 'dashboard:view'),
    ('MODERATOR', 'user:read'),
    ('MODERATOR', 'user:force_logout'),
    ('MODERATOR', 'user:warn'),
    ('MODERATOR', 'user:note:write'),
    ('MODERATOR', 'user:suspend'),
    ('MODERATOR', 'user:ban'),
    ('MODERATOR', 'user:unban'),
    ('MODERATOR', 'community:moderate'),
    ('MODERATOR', 'forum:moderate'),
    ('MODERATOR', 'story:moderate'),
    ('MODERATOR', 'event:read'),
    ('MODERATOR', 'media:read'),
    ('MODERATOR', 'media:delete'),
    ('MODERATOR', 'report:read'),
    ('MODERATOR', 'report:resolve'),
    ('MODERATOR', 'analytics:view'),
    ('MODERATOR', 'audit:read'),

    -- SUPPORT (9). Recovery actions only - deliberately cannot ban, suspend or delete.
    -- report:read without report:resolve (SSOT footnote 2).
    ('SUPPORT', 'dashboard:view'),
    ('SUPPORT', 'user:read'),
    ('SUPPORT', 'user:verify_email'),
    ('SUPPORT', 'user:reset_password'),
    ('SUPPORT', 'user:force_logout'),
    ('SUPPORT', 'user:warn'),
    ('SUPPORT', 'user:note:write'),
    ('SUPPORT', 'event:read'),
    ('SUPPORT', 'report:read'),

    -- CONTENT_MANAGER (13). Content, comms and localisation. No access to user records.
    ('CONTENT_MANAGER', 'dashboard:view'),
    ('CONTENT_MANAGER', 'badge:assign'),
    ('CONTENT_MANAGER', 'gamification:manage'),
    ('CONTENT_MANAGER', 'story:moderate'),
    ('CONTENT_MANAGER', 'event:read'),
    ('CONTENT_MANAGER', 'event:manage'),
    ('CONTENT_MANAGER', 'opportunity:manage'),
    ('CONTENT_MANAGER', 'media:read'),
    ('CONTENT_MANAGER', 'media:delete'),
    ('CONTENT_MANAGER', 'notification:send'),
    ('CONTENT_MANAGER', 'notification:template:manage'),
    ('CONTENT_MANAGER', 'analytics:view'),
    ('CONTENT_MANAGER', 'localization:manage'),

    -- FINANCE_ADMIN (7). user:read is the reduced identity+wallet view (SSOT footnote 1);
    -- audit:read is scoped to their own actions (footnote 4).
    ('FINANCE_ADMIN', 'dashboard:view'),
    ('FINANCE_ADMIN', 'user:read'),
    ('FINANCE_ADMIN', 'finance:read'),
    ('FINANCE_ADMIN', 'finance:ledger:read'),
    ('FINANCE_ADMIN', 'analytics:view'),
    ('FINANCE_ADMIN', 'analytics:export'),
    ('FINANCE_ADMIN', 'audit:read'),

    -- ANALYTICS_VIEWER (5). Strictly read-only. finance:read is aggregate figures only,
    -- never individual transactions (SSOT footnote 3).
    ('ANALYTICS_VIEWER', 'dashboard:view'),
    ('ANALYTICS_VIEWER', 'event:read'),
    ('ANALYTICS_VIEWER', 'finance:read'),
    ('ANALYTICS_VIEWER', 'analytics:view'),
    ('ANALYTICS_VIEWER', 'analytics:export')
) AS g(role_code, permission_code)
JOIN admin_roles r ON r.code = g.role_code
ON CONFLICT (admin_role_id, permission_code) DO NOTHING;

-- Deleting the system roles is enough: role_permissions cascades from admin_roles, so the grants go with them.
--rollback DELETE FROM admin_roles WHERE is_system = TRUE;
