--liquibase formatted sql

--changeset genlink:0044-create-quiz-matches-table
CREATE TABLE IF NOT EXISTS quiz_matches (
    match_id UUID PRIMARY KEY,
    quiz_id UUID NOT NULL,
    player1_id UUID NOT NULL,
    player2_id UUID,
    status VARCHAR(50) NOT NULL,
    player1_score INTEGER NOT NULL DEFAULT 0,
    player2_score INTEGER NOT NULL DEFAULT 0,
    winner_id UUID,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_quiz_matches_quiz_id FOREIGN KEY (quiz_id) REFERENCES quizzes(quiz_id),
    CONSTRAINT fk_quiz_matches_player1_id FOREIGN KEY (player1_id) REFERENCES users(user_id),
    CONSTRAINT fk_quiz_matches_player2_id FOREIGN KEY (player2_id) REFERENCES users(user_id),
    CONSTRAINT fk_quiz_matches_winner_id FOREIGN KEY (winner_id) REFERENCES users(user_id)
);

CREATE INDEX idx_quiz_matches_quiz_id ON quiz_matches(quiz_id);
CREATE INDEX idx_quiz_matches_player1_id ON quiz_matches(player1_id);
CREATE INDEX idx_quiz_matches_player2_id ON quiz_matches(player2_id);

--rollback DROP TABLE IF EXISTS quiz_matches;
