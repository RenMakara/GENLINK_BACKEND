--liquibase formatted sql

--changeset genlink:0078-create-post-reposts
-- A separate row per user makes repost/unrepost idempotent.
CREATE TABLE IF NOT EXISTS post_reposts (
    post_repost_id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    caption TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_post_reposts_post_id FOREIGN KEY (post_id) REFERENCES posts(post_id),
    CONSTRAINT fk_post_reposts_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_post_reposts_post_user UNIQUE (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_post_reposts_post_id ON post_reposts(post_id);
CREATE INDEX IF NOT EXISTS idx_post_reposts_user_id ON post_reposts(user_id);

--rollback DROP TABLE IF EXISTS post_reposts;
