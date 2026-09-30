--liquibase formatted sql

--changeset genlink:0023-seed-entertainment-quiz
-- One English sample quiz so the quiz endpoints are demoable. Khmer (title_km /
-- question_text_km / option_text_km) is left NULL - LocalizedTextSelector falls back to
-- English; real Khmer authoring is a content-team task.
INSERT INTO quizzes (quiz_id, title_en, category, difficulty, xp_reward) VALUES
    ('d0000000-0000-4000-8000-000000000001', 'Money Basics', 'FINANCE', 'EASY', 30);

INSERT INTO quiz_questions (question_id, quiz_id, question_text_en) VALUES
    ('d1000000-0000-4000-8000-000000000001', 'd0000000-0000-4000-8000-000000000001', 'What is a budget?'),
    ('d1000000-0000-4000-8000-000000000002', 'd0000000-0000-4000-8000-000000000001', 'What does "interest" on a loan mean?'),
    ('d1000000-0000-4000-8000-000000000003', 'd0000000-0000-4000-8000-000000000001', 'An emergency fund is best kept...');

INSERT INTO quiz_options (option_id, question_id, option_text_en, correct) VALUES
    ('d2000000-0000-4000-8000-000000000101', 'd1000000-0000-4000-8000-000000000001', 'A plan for your income and spending', TRUE),
    ('d2000000-0000-4000-8000-000000000102', 'd1000000-0000-4000-8000-000000000001', 'A type of bank account', FALSE),
    ('d2000000-0000-4000-8000-000000000103', 'd1000000-0000-4000-8000-000000000001', 'A government tax', FALSE),
    ('d2000000-0000-4000-8000-000000000104', 'd1000000-0000-4000-8000-000000000001', 'A kind of loan', FALSE),
    ('d2000000-0000-4000-8000-000000000201', 'd1000000-0000-4000-8000-000000000002', 'The extra amount you pay to borrow money', TRUE),
    ('d2000000-0000-4000-8000-000000000202', 'd1000000-0000-4000-8000-000000000002', 'A discount for paying early', FALSE),
    ('d2000000-0000-4000-8000-000000000203', 'd1000000-0000-4000-8000-000000000002', 'A government subsidy', FALSE),
    ('d2000000-0000-4000-8000-000000000204', 'd1000000-0000-4000-8000-000000000002', 'The total amount borrowed', FALSE),
    ('d2000000-0000-4000-8000-000000000301', 'd1000000-0000-4000-8000-000000000003', 'Invested in stocks', FALSE),
    ('d2000000-0000-4000-8000-000000000302', 'd1000000-0000-4000-8000-000000000003', 'Somewhere separate and easy to access', TRUE),
    ('d2000000-0000-4000-8000-000000000303', 'd1000000-0000-4000-8000-000000000003', 'Spent quickly', FALSE),
    ('d2000000-0000-4000-8000-000000000304', 'd1000000-0000-4000-8000-000000000003', 'Lent to friends', FALSE);

--rollback DELETE FROM quiz_options WHERE question_id IN ('d1000000-0000-4000-8000-000000000001','d1000000-0000-4000-8000-000000000002','d1000000-0000-4000-8000-000000000003'); DELETE FROM quiz_questions WHERE quiz_id = 'd0000000-0000-4000-8000-000000000001'; DELETE FROM quizzes WHERE quiz_id = 'd0000000-0000-4000-8000-000000000001';
