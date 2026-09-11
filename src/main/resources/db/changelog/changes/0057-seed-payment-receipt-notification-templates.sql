--liquibase formatted sql

--changeset genlink:0057-seed-payment-receipt-notification-templates
INSERT INTO notification_templates
    (template_id, template_key, title_en, title_km, message_en, message_km)
VALUES
    ('6f9a0000-0000-0000-0000-000000005216', 'COMMUNITY_EVENT_PAYMENT_RECEIPT',
     'Event payment completed', 'ការទូទាត់ព្រឹត្តិការណ៍បានបញ្ចប់',
     'Your simulated payment of {amount} {currency} for "{eventTitle}" was completed.',
     'ការទូទាត់សាកល្បងចំនួន {amount} {currency} សម្រាប់ «{eventTitle}» របស់អ្នកបានបញ្ចប់។'),
    ('6f9a0000-0000-0000-0000-000000005217', 'FINANCE_WALLET_PAYMENT_RECEIPT',
     'Payment completed', 'ការទូទាត់បានបញ្ចប់',
     'Your simulated payment of {amount} {currency} to {merchant} was completed via {provider}. Reference: {providerRef}.',
     'ការទូទាត់សាកល្បងចំនួន {amount} {currency} ទៅកាន់ {merchant} ត្រូវបានបញ្ចប់តាមរយៈ {provider}។ លេខយោង៖ {providerRef}។')
ON CONFLICT (template_key) DO NOTHING;

--rollback DELETE FROM notification_templates
--rollback WHERE template_key IN (
--rollback   'COMMUNITY_EVENT_PAYMENT_RECEIPT', 'FINANCE_WALLET_PAYMENT_RECEIPT');
