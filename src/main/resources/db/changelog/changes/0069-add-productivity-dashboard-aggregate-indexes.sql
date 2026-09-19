--liquibase formatted sql

--changeset genlink:0069-add-productivity-dashboard-aggregate-indexes
-- These indexes support the dashboard's database-side aggregates and top-five goal query.
CREATE INDEX IF NOT EXISTS idx_tasks_dashboard_user_state_due
    ON tasks (user_id, deleted_at, completed, due_date);

CREATE INDEX IF NOT EXISTS idx_focus_sessions_dashboard_user_state_started
    ON focus_sessions (user_id, completed, started_at);

CREATE INDEX IF NOT EXISTS idx_goals_dashboard_user_deleted_created
    ON goals (user_id, deleted_at, created_at DESC);

--rollback DROP INDEX IF EXISTS idx_goals_dashboard_user_deleted_created;
--rollback DROP INDEX IF EXISTS idx_focus_sessions_dashboard_user_state_started;
--rollback DROP INDEX IF EXISTS idx_tasks_dashboard_user_state_due;
