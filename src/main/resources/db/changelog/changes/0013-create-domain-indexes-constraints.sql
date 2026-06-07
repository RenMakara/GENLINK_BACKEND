--liquibase formatted sql

--changeset genlink:0013-create-domain-indexes-constraints
CREATE INDEX IF NOT EXISTS idx_posts_user_id ON posts(user_id);
CREATE INDEX IF NOT EXISTS idx_post_comments_post_id ON post_comments(post_id);
CREATE INDEX IF NOT EXISTS idx_chat_messages_chat_room_id ON chat_messages(chat_room_id);
CREATE INDEX IF NOT EXISTS idx_events_status ON events(status);
CREATE INDEX IF NOT EXISTS idx_habits_user_id ON habits(user_id);
CREATE INDEX IF NOT EXISTS idx_tasks_user_id ON tasks(user_id);
CREATE INDEX IF NOT EXISTS idx_expenses_user_id ON expenses(user_id);
CREATE INDEX IF NOT EXISTS idx_budgets_user_id ON budgets(user_id);
CREATE INDEX IF NOT EXISTS idx_saving_goals_user_id ON saving_goals(user_id);
CREATE INDEX IF NOT EXISTS idx_quiz_attempts_user_id ON quiz_attempts(user_id);
CREATE INDEX IF NOT EXISTS idx_user_challenges_user_id ON user_challenges(user_id);
CREATE INDEX IF NOT EXISTS idx_notifications_user_id ON notifications(user_id);
CREATE INDEX IF NOT EXISTS idx_leaderboards_category ON leaderboards(category);

--rollback DROP INDEX IF EXISTS idx_posts_user_id; DROP INDEX IF EXISTS idx_post_comments_post_id; DROP INDEX IF EXISTS idx_chat_messages_chat_room_id; DROP INDEX IF EXISTS idx_events_status; DROP INDEX IF EXISTS idx_habits_user_id; DROP INDEX IF EXISTS idx_tasks_user_id; DROP INDEX IF EXISTS idx_expenses_user_id; DROP INDEX IF EXISTS idx_budgets_user_id; DROP INDEX IF EXISTS idx_saving_goals_user_id; DROP INDEX IF EXISTS idx_quiz_attempts_user_id; DROP INDEX IF EXISTS idx_user_challenges_user_id; DROP INDEX IF EXISTS idx_notifications_user_id; DROP INDEX IF EXISTS idx_leaderboards_category;
