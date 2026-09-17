--liquibase formatted sql

--changeset genlink:0066-seed-chat-and-social-notification-templates
INSERT INTO notification_templates
    (template_id, template_key, title_en, title_km, message_en, message_km)
VALUES
    ('6f9a0000-0000-0000-0000-000000006601', 'CHAT_NEW_MESSAGE',
     'New message from {senderName}', 'សារថ្មីពី {senderName}',
     '{preview}', '{preview}'),
    ('6f9a0000-0000-0000-0000-000000006602', 'COMMUNITY_FOLLOW_REQUEST',
     'New follow request', 'សំណើតាមដានថ្មី',
     '{followerName} requested to follow you.',
     '{followerName} បានស្នើសុំតាមដានអ្នក។'),
    ('6f9a0000-0000-0000-0000-000000006603', 'COMMUNITY_NEW_FOLLOWER',
     'New follower', 'អ្នកតាមដានថ្មី',
     '{followerName} started following you.',
     '{followerName} បានចាប់ផ្តើមតាមដានអ្នក។'),
    ('6f9a0000-0000-0000-0000-000000006604', 'COMMUNITY_FOLLOW_ACCEPTED',
     'Follow request accepted', 'សំណើតាមដានត្រូវបានទទួលយក',
     '{userName} accepted your follow request.',
     '{userName} បានទទួលយកសំណើតាមដានរបស់អ្នក។'),
    ('6f9a0000-0000-0000-0000-000000006605', 'COMMUNITY_POST_COMMENT',
     'New comment on your post', 'មតិយោបល់ថ្មីលើការបង្ហោះរបស់អ្នក',
     '{commenterName} commented: "{commentSnippet}"',
     '{commenterName} បានបញ្ចេញមតិ៖ «{commentSnippet}»'),
    ('6f9a0000-0000-0000-0000-000000006606', 'COMMUNITY_COMMENT_REPLY',
     'New reply to your comment', 'ការឆ្លើយតបថ្មីចំពោះមតិយោបល់របស់អ្នក',
     '{replierName} replied: "{replySnippet}"',
     '{replierName} បានឆ្លើយតប៖ «{replySnippet}»'),
    ('6f9a0000-0000-0000-0000-000000006607', 'COMMUNITY_POST_LIKE',
     'New like on your post', 'ការចូលចិត្តថ្មីលើការបង្ហោះរបស់អ្នក',
     '{likerName} liked your post.',
     '{likerName} បានចូលចិត្តការបង្ហោះរបស់អ្នក។')
ON CONFLICT (template_key) DO NOTHING;

--rollback DELETE FROM notification_templates
--rollback WHERE template_key IN (
--rollback   'CHAT_NEW_MESSAGE', 'COMMUNITY_FOLLOW_REQUEST',
--rollback   'COMMUNITY_NEW_FOLLOWER', 'COMMUNITY_FOLLOW_ACCEPTED',
--rollback   'COMMUNITY_POST_COMMENT', 'COMMUNITY_COMMENT_REPLY',
--rollback   'COMMUNITY_POST_LIKE');
