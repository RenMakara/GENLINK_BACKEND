--liquibase formatted sql

--changeset genlink:0007-create-productivity-tables
CREATE TABLE IF NOT EXISTS habits (
    habit_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    category VARCHAR(80),
    frequency VARCHAR(50),
    reminder_time TIME,
    streak_count INTEGER NOT NULL DEFAULT 0,
    completion_rate NUMERIC(5,2),
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_habits_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS habit_check_ins (
    check_in_id UUID PRIMARY KEY,
    habit_id UUID NOT NULL,
    user_id UUID NOT NULL,
    check_in_date DATE NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_habit_check_ins_habit_id FOREIGN KEY (habit_id) REFERENCES habits(habit_id),
    CONSTRAINT fk_habit_check_ins_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_habit_check_ins_habit_user_date UNIQUE (habit_id, user_id, check_in_date)
);

CREATE TABLE IF NOT EXISTS tasks (
    task_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    priority VARCHAR(30),
    category VARCHAR(80),
    due_date TIMESTAMP,
    reminder_time TIMESTAMP,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_tasks_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS focus_sessions (
    session_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    mode VARCHAR(50),
    duration_minutes INTEGER NOT NULL,
    ambient_sound VARCHAR(80),
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    xp_earned INTEGER NOT NULL DEFAULT 0,
    started_at TIMESTAMP NOT NULL,
    ended_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_focus_sessions_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS goals (
    goal_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    description TEXT,
    goal_type VARCHAR(50),
    deadline DATE,
    progress_percentage NUMERIC(5,2),
    status VARCHAR(50),
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_goals_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS goal_milestones (
    milestone_id UUID PRIMARY KEY,
    goal_id UUID NOT NULL,
    title VARCHAR(160) NOT NULL,
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_goal_milestones_goal_id FOREIGN KEY (goal_id) REFERENCES goals(goal_id)
);

CREATE TABLE IF NOT EXISTS motivation_contents (
    motivation_id UUID PRIMARY KEY,
    title_en VARCHAR(180) NOT NULL,
    title_km VARCHAR(180),
    content_en TEXT NOT NULL,
    content_km TEXT,
    category VARCHAR(80),
    saved_count INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH'
);

CREATE TABLE IF NOT EXISTS user_saved_motivations (
    saved_motivation_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    motivation_id UUID NOT NULL,
    saved_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_user_saved_motivations_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_user_saved_motivations_motivation_id FOREIGN KEY (motivation_id) REFERENCES motivation_contents(motivation_id),
    CONSTRAINT uk_user_saved_motivations_user_motivation UNIQUE (user_id, motivation_id)
);

--rollback DROP TABLE IF EXISTS user_saved_motivations; DROP TABLE IF EXISTS motivation_contents; DROP TABLE IF EXISTS goal_milestones; DROP TABLE IF EXISTS goals; DROP TABLE IF EXISTS focus_sessions; DROP TABLE IF EXISTS tasks; DROP TABLE IF EXISTS habit_check_ins; DROP TABLE IF EXISTS habits;
