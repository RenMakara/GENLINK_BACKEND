--liquibase formatted sql

--changeset genlink:0054-seed-forum-notification-template
INSERT INTO notification_templates
    (template_id, template_key, title_en, title_km, message_en, message_km)
VALUES
    ('6f9a0000-0000-0000-0000-000000005212', 'FORUM_DISCUSSION_MODERATED',
     'Forum discussion update', 'បច្ចុប្បន្នភាពការពិភាក្សាវេទិកា',
     'Your forum discussion "{discussionTitle}" was {moderationAction}.',
     'ការពិភាក្សាវេទិការបស់អ្នក «{discussionTitle}» ត្រូវបាន {moderationAction}')
ON CONFLICT (template_key) DO NOTHING;

--rollback DELETE FROM notification_templates
--rollback WHERE template_key = 'FORUM_DISCUSSION_MODERATED';
