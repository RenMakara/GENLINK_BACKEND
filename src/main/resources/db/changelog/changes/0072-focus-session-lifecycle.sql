--liquibase formatted sql

--changeset genlink:0072-focus-session-lifecycle
ALTER TABLE focus_sessions
    ADD COLUMN IF NOT EXISTS status VARCHAR(20);

ALTER TABLE focus_sessions
    ADD COLUMN IF NOT EXISTS focused_seconds INTEGER NOT NULL DEFAULT 0;

ALTER TABLE focus_sessions
    ADD COLUMN IF NOT EXISTS active_started_at TIMESTAMP;

ALTER TABLE focus_sessions
    ADD COLUMN IF NOT EXISTS paused_at TIMESTAMP;

-- Existing incomplete rows have no reliable client/session state to restore.
UPDATE focus_sessions
SET status = CASE WHEN completed = TRUE THEN 'COMPLETED' ELSE 'ABANDONED' END
WHERE status IS NULL;

UPDATE focus_sessions
SET focused_seconds = duration_minutes * 60
WHERE completed = TRUE AND focused_seconds = 0;

ALTER TABLE focus_sessions
    ALTER COLUMN status SET DEFAULT 'ACTIVE';

ALTER TABLE focus_sessions
    ALTER COLUMN status SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_focus_sessions_user_status_started
    ON focus_sessions (user_id, status, started_at DESC);

CREATE UNIQUE INDEX IF NOT EXISTS uk_focus_sessions_one_active
    ON focus_sessions (user_id)
    WHERE status IN ('ACTIVE', 'PAUSED');

--rollback DROP INDEX IF EXISTS uk_focus_sessions_one_active;
--rollback DROP INDEX IF EXISTS idx_focus_sessions_user_status_started;
--rollback ALTER TABLE focus_sessions DROP COLUMN IF EXISTS paused_at;
--rollback ALTER TABLE focus_sessions DROP COLUMN IF EXISTS active_started_at;
--rollback ALTER TABLE focus_sessions DROP COLUMN IF EXISTS focused_seconds;
--rollback ALTER TABLE focus_sessions DROP COLUMN IF EXISTS status;
