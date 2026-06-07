--liquibase formatted sql

--changeset genlink:0010-create-entertainment-tables
CREATE TABLE IF NOT EXISTS quizzes (
    quiz_id UUID PRIMARY KEY,
    title_en VARCHAR(180) NOT NULL,
    title_km VARCHAR(180),
    category VARCHAR(80),
    difficulty VARCHAR(50) NOT NULL,
    xp_reward INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH'
);

CREATE TABLE IF NOT EXISTS quiz_questions (
    question_id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL,
    question_text_en TEXT NOT NULL,
    question_text_km TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_quiz_questions_quiz_id FOREIGN KEY (quiz_id) REFERENCES quizzes(quiz_id)
);

CREATE TABLE IF NOT EXISTS quiz_options (
    option_id UUID PRIMARY KEY,
    question_id UUID NOT NULL,
    option_text_en TEXT NOT NULL,
    option_text_km TEXT,
    correct BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_quiz_options_question_id FOREIGN KEY (question_id) REFERENCES quiz_questions(question_id)
);

CREATE TABLE IF NOT EXISTS quiz_attempts (
    attempt_id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL,
    user_id UUID NOT NULL,
    score INTEGER NOT NULL DEFAULT 0,
    correct_count INTEGER NOT NULL DEFAULT 0,
    total_questions INTEGER NOT NULL DEFAULT 0,
    xp_earned INTEGER NOT NULL DEFAULT 0,
    completed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_quiz_attempts_quiz_id FOREIGN KEY (quiz_id) REFERENCES quizzes(quiz_id),
    CONSTRAINT fk_quiz_attempts_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS quiz_attempt_answers (
    answer_id UUID PRIMARY KEY,
    attempt_id UUID NOT NULL,
    question_id UUID NOT NULL,
    selected_option_id UUID NOT NULL,
    correct BOOLEAN NOT NULL DEFAULT FALSE,
    answered_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_quiz_attempt_answers_attempt_id FOREIGN KEY (attempt_id) REFERENCES quiz_attempts(attempt_id),
    CONSTRAINT fk_quiz_attempt_answers_question_id FOREIGN KEY (question_id) REFERENCES quiz_questions(question_id),
    CONSTRAINT fk_quiz_attempt_answers_selected_option_id FOREIGN KEY (selected_option_id) REFERENCES quiz_options(option_id)
);

CREATE TABLE IF NOT EXISTS daily_challenges (
    challenge_id UUID PRIMARY KEY,
    title_en VARCHAR(180) NOT NULL,
    title_km VARCHAR(180),
    description_en TEXT NOT NULL,
    description_km TEXT,
    challenge_type VARCHAR(50) NOT NULL,
    xp_reward INTEGER NOT NULL DEFAULT 0,
    deadline TIMESTAMP,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH'
);

CREATE TABLE IF NOT EXISTS user_challenges (
    user_challenge_id UUID PRIMARY KEY,
    challenge_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    progress INTEGER NOT NULL DEFAULT 0,
    completed_at TIMESTAMP,
    claimed_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_user_challenges_challenge_id FOREIGN KEY (challenge_id) REFERENCES daily_challenges(challenge_id),
    CONSTRAINT fk_user_challenges_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_user_challenges_challenge_user UNIQUE (challenge_id, user_id)
);

CREATE TABLE IF NOT EXISTS xp_history (
    xp_history_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    source_type VARCHAR(80) NOT NULL,
    source_id UUID,
    xp_amount INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_xp_history_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS badges (
    badge_id UUID PRIMARY KEY,
    name_en VARCHAR(180) NOT NULL,
    name_km VARCHAR(180),
    description_en TEXT,
    description_km TEXT,
    requirement TEXT,
    icon_url TEXT,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH'
);

CREATE TABLE IF NOT EXISTS user_badges (
    user_badge_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    badge_id UUID NOT NULL,
    unlocked_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_user_badges_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_user_badges_badge_id FOREIGN KEY (badge_id) REFERENCES badges(badge_id),
    CONSTRAINT uk_user_badges_user_badge UNIQUE (user_id, badge_id)
);

CREATE TABLE IF NOT EXISTS leaderboards (
    leaderboard_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    category VARCHAR(50) NOT NULL,
    period VARCHAR(50) NOT NULL,
    rank INTEGER NOT NULL DEFAULT 0,
    points INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_leaderboards_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_leaderboards_user_category_period UNIQUE (user_id, category, period)
);

CREATE TABLE IF NOT EXISTS short_contents (
    short_content_id UUID PRIMARY KEY,
    title_en VARCHAR(180) NOT NULL,
    title_km VARCHAR(180),
    description_en TEXT,
    description_km TEXT,
    category VARCHAR(80),
    video_url TEXT,
    thumbnail_url TEXT,
    creator_id UUID,
    like_count INTEGER NOT NULL DEFAULT 0,
    save_count INTEGER NOT NULL DEFAULT 0,
    share_count INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_short_contents_creator_id FOREIGN KEY (creator_id) REFERENCES users(user_id)
);

--rollback DROP TABLE IF EXISTS short_contents; DROP TABLE IF EXISTS leaderboards; DROP TABLE IF EXISTS user_badges; DROP TABLE IF EXISTS badges; DROP TABLE IF EXISTS xp_history; DROP TABLE IF EXISTS user_challenges; DROP TABLE IF EXISTS daily_challenges; DROP TABLE IF EXISTS quiz_attempt_answers; DROP TABLE IF EXISTS quiz_attempts; DROP TABLE IF EXISTS quiz_options; DROP TABLE IF EXISTS quiz_questions; DROP TABLE IF EXISTS quizzes;
