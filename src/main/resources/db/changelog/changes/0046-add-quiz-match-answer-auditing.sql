--liquibase formatted sql

--changeset genlink:0046-add-quiz-match-answer-auditing
-- QuizMatchAnswer extends BaseEntity, so Hibernate requires the same auditing
-- columns used by every other BaseEntity-backed table. Migration 0045 created
-- the timestamps but omitted the auditor names.
ALTER TABLE quiz_match_answers
    ADD COLUMN created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    ADD COLUMN updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH';

--rollback ALTER TABLE quiz_match_answers DROP COLUMN updated_by, DROP COLUMN created_by;
