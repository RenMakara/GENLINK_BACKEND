--liquibase formatted sql

--changeset genlink:0024-add-badge-code-and-seed
-- Badges auto-unlock on milestones (per the Entertainment reward flow).
ALTER TABLE badges ADD COLUMN IF NOT EXISTS code VARCHAR(80);
CREATE UNIQUE INDEX IF NOT EXISTS uk_badges_code ON badges(code);

INSERT INTO badges (badge_id, code, name_en, description_en, requirement) VALUES
    ('e0000000-0000-4000-8000-000000000001', 'PRODUCTIVITY_HERO', 'Productivity Hero', 'Racked up productivity wins across habits, tasks and focus.', 'Earn XP from 10 productivity actions (habits, tasks, focus sessions).'),
    ('e0000000-0000-4000-8000-000000000002', 'COMMUNICATION_HELPER', 'Communication Helper', 'An engaged member of the community.', 'Reach 300 total XP.'),
    ('e0000000-0000-4000-8000-000000000003', 'FINANCIAL_STARTER', 'Financial Starter', 'Took the first step in financial literacy.', 'Finish your first financial lesson.'),
    ('e0000000-0000-4000-8000-000000000004', 'CONSISTENCY_MASTER', 'Consistency Master', 'Kept the habit going.', 'Complete 7 habit check-ins.'),
    ('e0000000-0000-4000-8000-000000000005', 'QUIZ_CHAMPION', 'Quiz Champion', 'A quiz winner.', 'Earn XP on 3 different quizzes.'),
    ('e0000000-0000-4000-8000-000000000006', 'EVENT_EXPLORER', 'Event Explorer', 'Out and about at community events.', 'RSVP to 3 events.');

--rollback DELETE FROM badges WHERE code IN ('PRODUCTIVITY_HERO','COMMUNICATION_HELPER','FINANCIAL_STARTER','CONSISTENCY_MASTER','QUIZ_CHAMPION','EVENT_EXPLORER'); DROP INDEX IF EXISTS uk_badges_code; ALTER TABLE badges DROP COLUMN IF EXISTS code;
