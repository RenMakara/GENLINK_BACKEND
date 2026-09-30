--liquibase formatted sql

--changeset genlink:0025-seed-daily-challenges
-- English sample challenges so the challenge list + the Home daily-challenge card have content.
INSERT INTO daily_challenges (challenge_id, title_en, description_en, challenge_type, xp_reward, deadline) VALUES
    ('f0000000-0000-4000-8000-000000000001', 'Check in on a habit', 'Complete one habit check-in today.', 'HABIT', 15, '2026-12-31 23:59:59'),
    ('f0000000-0000-4000-8000-000000000002', 'Take a quiz', 'Finish any quiz and see your score.', 'QUIZ', 20, '2026-12-31 23:59:59'),
    ('f0000000-0000-4000-8000-000000000003', 'Log an expense', 'Track one expense to stay on top of your budget.', 'FINANCE', 15, '2026-12-31 23:59:59');

--rollback DELETE FROM user_challenges WHERE challenge_id IN ('f0000000-0000-4000-8000-000000000001','f0000000-0000-4000-8000-000000000002','f0000000-0000-4000-8000-000000000003'); DELETE FROM daily_challenges WHERE challenge_id IN ('f0000000-0000-4000-8000-000000000001','f0000000-0000-4000-8000-000000000002','f0000000-0000-4000-8000-000000000003');
