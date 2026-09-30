--liquibase formatted sql

--changeset genlink:0083-one-poll-per-post
-- The application resolves one poll per post by post_id.
CREATE UNIQUE INDEX IF NOT EXISTS uk_polls_post_id ON polls(post_id);

--rollback DROP INDEX IF EXISTS uk_polls_post_id;
