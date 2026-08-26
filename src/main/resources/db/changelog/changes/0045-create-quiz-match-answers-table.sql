-- liquibase formatted sql
-- changeset antigravity:0045-create-quiz-match-answers-table

CREATE TABLE quiz_match_answers (
    answer_id UUID PRIMARY KEY,
    match_id UUID NOT NULL REFERENCES quiz_matches(match_id) ON DELETE CASCADE,
    player_id UUID NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    question_id UUID NOT NULL REFERENCES quiz_questions(question_id) ON DELETE CASCADE,
    option_id UUID NOT NULL REFERENCES quiz_options(option_id) ON DELETE CASCADE,
    is_correct BOOLEAN NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    updated_at TIMESTAMP WITH TIME ZONE NOT NULL DEFAULT NOW(),
    version BIGINT NOT NULL DEFAULT 0,

    -- Prevent duplicate answers for the same question in the same match by the same player
    CONSTRAINT uq_match_player_question UNIQUE (match_id, player_id, question_id)
);

CREATE INDEX idx_quiz_match_answers_match_id ON quiz_match_answers(match_id);
CREATE INDEX idx_quiz_match_answers_player_id ON quiz_match_answers(player_id);
