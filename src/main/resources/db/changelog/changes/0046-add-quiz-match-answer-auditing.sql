--liquibase formatted sql

--changeset genlink:0046-add-quiz-match-answer-auditing
-- Adds the audit columns required by BaseEntity.
ALTER TABLE quiz_match_answers
    ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    ADD COLUMN updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH';

--rollback ALTER TABLE quiz_match_answers DROP COLUMN updated_by, DROP COLUMN created_by;
