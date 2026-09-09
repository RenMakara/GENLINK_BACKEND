--liquibase formatted sql

--changeset genlink:0055-seed-event-reminder-template
INSERT INTO notification_templates
    (template_id, template_key, title_en, title_km, message_en, message_km)
VALUES
    ('6f9a0000-0000-0000-0000-000000005213', 'COMMUNITY_EVENT_REMINDER',
     'Event reminder', 'ការរំលឹកព្រឹត្តិការណ៍',
     'Reminder: {eventTitle} starts at {startsAt}.',
     'រំលឹក៖ {eventTitle} ចាប់ផ្តើមនៅ {startsAt}។')
ON CONFLICT (template_key) DO NOTHING;

--rollback DELETE FROM notification_templates
--rollback WHERE template_key = 'COMMUNITY_EVENT_REMINDER';
