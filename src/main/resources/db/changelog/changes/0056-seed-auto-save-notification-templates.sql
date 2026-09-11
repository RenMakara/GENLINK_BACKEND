--liquibase formatted sql

--changeset genlink:0056-seed-auto-save-notification-templates
INSERT INTO notification_templates
    (template_id, template_key, title_en, title_km, message_en, message_km)
VALUES
    ('6f9a0000-0000-0000-0000-000000005214', 'FINANCE_AUTO_SAVE_APPLIED',
     'Auto-save applied', 'បានអនុវត្តការសន្សំស្វ័យប្រវត្តិ',
     'Auto-save added {amount} {currency} to your goal "{goalName}".',
     'ការសន្សំស្វ័យប្រវត្តិបានបញ្ចូល {amount} {currency} ទៅក្នុងគោលដៅ «{goalName}» របស់អ្នក។'),
    ('6f9a0000-0000-0000-0000-000000005215', 'FINANCE_AUTO_SAVE_SKIPPED',
     'Auto-save skipped', 'បានរំលងការសន្សំស្វ័យប្រវត្តិ',
     'Auto-save of {amount} {currency} for "{goalName}" was skipped because your wallet balance was too low.',
     'ការសន្សំស្វ័យប្រវត្តិចំនួន {amount} {currency} សម្រាប់ «{goalName}» ត្រូវបានរំលង ព្រោះសមតុល្យកាបូបរបស់អ្នកមិនគ្រប់គ្រាន់។')
ON CONFLICT (template_key) DO NOTHING;

--rollback DELETE FROM notification_templates
--rollback WHERE template_key IN (
--rollback   'FINANCE_AUTO_SAVE_APPLIED', 'FINANCE_AUTO_SAVE_SKIPPED');
