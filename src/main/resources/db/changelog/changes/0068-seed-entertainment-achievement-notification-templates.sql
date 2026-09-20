--liquibase formatted sql

--changeset genlink:0068-seed-entertainment-achievement-notification-templates
INSERT INTO notification_templates
    (template_id, template_key, title_en, title_km, message_en, message_km)
VALUES
    ('6f9a0000-0000-0000-0000-000000005219', 'ENTERTAINMENT_BADGE_AWARDED',
     'Badge unlocked', 'បានដោះសោស្លាកសញ្ញា',
     'You unlocked the {badgeName} badge.',
     'អ្នកបានដោះសោស្លាកសញ្ញា {badgeName}។'),
    ('6f9a0000-0000-0000-0000-000000005220', 'ENTERTAINMENT_LEVEL_UP',
     'Level up', 'ឡើងកម្រិត',
     'You reached level {level} with {totalXp} XP.',
     'អ្នកបានឈានដល់កម្រិត {level} ជាមួយ XP {totalXp}។')
ON CONFLICT (template_key) DO NOTHING;

--rollback DELETE FROM notification_templates
--rollback WHERE template_key IN ('ENTERTAINMENT_BADGE_AWARDED', 'ENTERTAINMENT_LEVEL_UP');
