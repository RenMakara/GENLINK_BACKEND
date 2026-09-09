--liquibase formatted sql

--changeset genlink:0058-seed-goal-milestone-notification-template
INSERT INTO notification_templates
    (template_id, template_key, title_en, title_km, message_en, message_km)
VALUES
    ('6f9a0000-0000-0000-0000-000000005218', 'PRODUCTIVITY_GOAL_MILESTONE',
     'Goal milestone completed', 'បានបញ្ចប់ចំណុចសំខាន់នៃគោលដៅ',
     'You completed milestone {milestoneTitle} for {goalTitle}. Progress: {progressPercent}%.',
     'អ្នកបានបញ្ចប់ចំណុចសំខាន់ {milestoneTitle} សម្រាប់គោលដៅ {goalTitle}។ វឌ្ឍនភាព៖ {progressPercent}%។')
ON CONFLICT (template_key) DO NOTHING;

--rollback DELETE FROM notification_templates
--rollback WHERE template_key = 'PRODUCTIVITY_GOAL_MILESTONE';
