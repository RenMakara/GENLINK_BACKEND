--liquibase formatted sql

--changeset genlink:0076-seed-story-like-notification-template
INSERT INTO notification_templates
    (template_id, template_key, title_en, title_km, message_en, message_km)
VALUES
    ('6f9a0000-0000-0000-0000-000000007601', 'COMMUNITY_STORY_LIKE',
     'New like on your story', 'ការចូលចិត្តថ្មីលើរឿងរបស់អ្នក',
     '{likerName} liked your story.',
     '{likerName} បានចូលចិត្តរឿងរបស់អ្នក។')
ON CONFLICT (template_key) DO NOTHING;

--rollback DELETE FROM notification_templates WHERE template_key = 'COMMUNITY_STORY_LIKE';
