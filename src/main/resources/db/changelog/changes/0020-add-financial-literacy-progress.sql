--liquibase formatted sql

--changeset genlink:0020-add-financial-literacy-progress
-- Phase 4 (Financial Literacy) closes two gaps from migration 0008: nothing tracked
-- whether a user finished a lesson, and nothing recorded a quiz result. Without
-- lesson_progress the lesson XP + learning reward could be paid on every retake;
-- the `rewarded` flag makes the whole reward bundle (XP + wallet credit) once-only.
CREATE TABLE IF NOT EXISTS lesson_progress (
    progress_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    lesson_id UUID NOT NULL,
    completed_at TIMESTAMP,
    best_score INTEGER,
    quiz_passed BOOLEAN NOT NULL DEFAULT FALSE,
    rewarded BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_lesson_progress_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_lesson_progress_lesson_id FOREIGN KEY (lesson_id) REFERENCES financial_lessons(lesson_id),
    CONSTRAINT uk_lesson_progress_user_lesson UNIQUE (user_id, lesson_id)
);

-- One row per submission, so retake history survives even though lesson_progress only
-- keeps the best score. Separate from lesson_progress on purpose: progress is durable
-- per-lesson state, attempts are an append-only log.
CREATE TABLE IF NOT EXISTS finance_quiz_attempts (
    attempt_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    lesson_id UUID NOT NULL,
    score INTEGER NOT NULL,
    passed BOOLEAN NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_finance_quiz_attempts_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_finance_quiz_attempts_lesson_id FOREIGN KEY (lesson_id) REFERENCES financial_lessons(lesson_id)
);

-- Short Video lessons need somewhere for the media URL; the content_* columns hold the
-- article/tip body.
ALTER TABLE financial_lessons ADD COLUMN IF NOT EXISTS media_url VARCHAR(500);

CREATE INDEX IF NOT EXISTS idx_lesson_progress_user_id ON lesson_progress(user_id);
CREATE INDEX IF NOT EXISTS idx_finance_quiz_attempts_user_lesson ON finance_quiz_attempts(user_id, lesson_id);

--rollback DROP TABLE IF EXISTS finance_quiz_attempts; DROP TABLE IF EXISTS lesson_progress; ALTER TABLE financial_lessons DROP COLUMN IF EXISTS media_url;
