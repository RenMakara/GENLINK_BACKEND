--liquibase formatted sql

--changeset genlink:0071-sync-user-language-preference-columns
-- Settings are the user-facing source for this preference.
UPDATE users u
SET preferred_language = s.language
FROM user_settings s
WHERE s.user_id = u.user_id
  AND s.language IN ('ENGLISH', 'KHMER')
  AND u.preferred_language IS DISTINCT FROM s.language;
