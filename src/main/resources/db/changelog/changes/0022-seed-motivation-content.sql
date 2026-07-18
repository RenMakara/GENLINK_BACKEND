--liquibase formatted sql

--changeset genlink:0022-seed-motivation-content
-- English motivation entries so the daily-motivation card and the motivation list have
-- content. Khmer (title_km/content_km) is left NULL - LocalizedTextSelector falls back to
-- English, and real Khmer authoring is a content-team task, not machine translation.
INSERT INTO motivation_contents (motivation_id, title_en, content_en, category, saved_count) VALUES
    ('c0000000-0000-4000-8000-000000000001', 'Start small', 'You do not have to be great to start, but you have to start to be great. One small step today beats a perfect plan tomorrow.', 'MINDSET', 0),
    ('c0000000-0000-4000-8000-000000000002', 'Focus on one thing', 'Multitasking splits your attention and slows you down. Pick the one task that matters most and give it your full focus.', 'FOCUS', 0),
    ('c0000000-0000-4000-8000-000000000003', 'Progress over perfection', 'Done is better than perfect. Ship the work, learn from it, and improve on the next pass.', 'MINDSET', 0),
    ('c0000000-0000-4000-8000-000000000004', 'Keep showing up', 'Consistency beats intensity. Small efforts repeated every day compound into big results.', 'PERSISTENCE', 0),
    ('c0000000-0000-4000-8000-000000000005', 'Rest is part of the work', 'Your brain needs recovery to learn. A short break or a good night''s sleep makes your next study session sharper.', 'WELLBEING', 0),
    ('c0000000-0000-4000-8000-000000000006', 'Your only competition is yesterday', 'Do not compare yourself to others. Aim to be a little better than you were yesterday.', 'MINDSET', 0);

--rollback DELETE FROM motivation_contents WHERE motivation_id IN ('c0000000-0000-4000-8000-000000000001','c0000000-0000-4000-8000-000000000002','c0000000-0000-4000-8000-000000000003','c0000000-0000-4000-8000-000000000004','c0000000-0000-4000-8000-000000000005','c0000000-0000-4000-8000-000000000006');
