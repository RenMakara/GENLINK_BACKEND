--liquibase formatted sql

--changeset genlink:0052-seed-notification-templates
INSERT INTO notification_templates
    (template_id, template_key, title_en, title_km, message_en, message_km)
VALUES
    ('6f9a0000-0000-0000-0000-000000005201', 'COMMUNITY_EVENT_RSVP_ORGANIZER',
     'New event registration', 'មានការចុះឈ្មោះចូលរួមព្រឹត្តិការណ៍ថ្មី',
     '{attendeeName} registered for your event: {eventTitle}.',
     '{attendeeName} បានចុះឈ្មោះចូលរួមព្រឹត្តិការណ៍របស់អ្នក៖ {eventTitle}'),
    ('6f9a0000-0000-0000-0000-000000005202', 'COMMUNITY_JOIN_REQUEST_OWNER',
     'New community join request', 'សំណើចូលរួមសហគមន៍ថ្មី',
     '{requesterName} requested to join {communityName}.',
     '{requesterName} បានស្នើសុំចូលរួម {communityName}'),
    ('6f9a0000-0000-0000-0000-000000005203', 'COMMUNITY_JOIN_REQUEST_ACCEPTED',
     'Community request approved', 'សំណើចូលរួមសហគមន៍ត្រូវបានអនុម័ត',
     'Your request to join {communityName} was approved.',
     'សំណើរបស់អ្នកដើម្បីចូលរួម {communityName} ត្រូវបានអនុម័ត'),
    ('6f9a0000-0000-0000-0000-000000005204', 'COMMUNITY_POST_MODERATED',
     'Community post update', 'បច្ចុប្បន្នភាពប្រកាសសហគមន៍',
     'Your post in {communityName} was {moderationAction}.',
     'ប្រកាសរបស់អ្នកក្នុង {communityName} ត្រូវបាន {moderationAction}'),
    ('6f9a0000-0000-0000-0000-000000005205', 'FINANCE_BUDGET_APPROACHING',
     'Budget limit approaching', 'ថវិកាជិតដល់កំណត់',
     '{budgetName} has reached {percent}% of its limit.',
     '{budgetName} បានប្រើប្រាស់ដល់ {percent}% នៃកំណត់ថវិការបស់វា'),
    ('6f9a0000-0000-0000-0000-000000005206', 'FINANCE_BUDGET_EXCEEDED',
     'Budget limit exceeded', 'ថវិកាលើសកំណត់',
     '{budgetName} has exceeded its limit.',
     '{budgetName} បានលើសកំណត់ថវិការបស់វា'),
    ('6f9a0000-0000-0000-0000-000000005207', 'FINANCE_SAVING_GOAL_REACHED',
     'Saving goal reached', 'សម្រេចបានគោលដៅសន្សំប្រាក់',
     'You reached your saving goal: {goalName}.',
     'អ្នកសម្រេចបានគោលដៅសន្សំប្រាក់របស់អ្នក៖ {goalName}'),
    ('6f9a0000-0000-0000-0000-000000005208', 'PRODUCTIVITY_TASK_DUE',
     'Task due soon', 'កិច្ចការជិតដល់ពេលកំណត់',
     'Your task {taskTitle} is due at {dueAt}.',
     'កិច្ចការ {taskTitle} របស់អ្នកដល់ពេលកំណត់នៅ {dueAt}'),
    ('6f9a0000-0000-0000-0000-000000005209', 'PRODUCTIVITY_HABIT_STREAK',
     'Habit streak update', 'បច្ចុប្បន្នភាពទម្លាប់ជាប់គ្នា',
     'You have a {streakDays}-day streak for {habitName}.',
     'អ្នកមានទម្លាប់ {habitName} ជាប់គ្នា {streakDays} ថ្ងៃ'),
    ('6f9a0000-0000-0000-0000-000000005210', 'ENTERTAINMENT_CHALLENGE_COMPLETE',
     'Challenge completed', 'បានបញ្ចប់ការប្រកួតប្រជែង',
     'You completed {challengeName} and earned {rewardXp} XP.',
     'អ្នកបានបញ្ចប់ {challengeName} និងទទួលបាន {rewardXp} XP'),
    ('6f9a0000-0000-0000-0000-000000005211', 'SECURITY_SUSPICIOUS_LOGIN',
     'Security alert', 'ការជូនដំណឹងសុវត្ថិភាព',
     'A suspicious login was detected for your account from {loginLocation}.',
     'បានរកឃើញការចូលគណនីគួរឱ្យសង្ស័យពី {loginLocation}')
ON CONFLICT (template_key) DO NOTHING;

--rollback DELETE FROM notification_templates
--rollback WHERE template_key IN (
--rollback   'COMMUNITY_EVENT_RSVP_ORGANIZER', 'COMMUNITY_JOIN_REQUEST_OWNER',
--rollback   'COMMUNITY_JOIN_REQUEST_ACCEPTED', 'COMMUNITY_POST_MODERATED',
--rollback   'FINANCE_BUDGET_APPROACHING', 'FINANCE_BUDGET_EXCEEDED',
--rollback   'FINANCE_SAVING_GOAL_REACHED', 'PRODUCTIVITY_TASK_DUE',
--rollback   'PRODUCTIVITY_HABIT_STREAK', 'ENTERTAINMENT_CHALLENGE_COMPLETE',
--rollback   'SECURITY_SUSPICIOUS_LOGIN');
