--liquibase formatted sql

--changeset genlink:0079-create-discussion-reposts
-- Forum discussions are a separate content type from posts, so their repost records
-- must point at forum_discussions rather than being sent through the post table.
CREATE TABLE IF NOT EXISTS discussion_reposts (
    discussion_repost_id UUID PRIMARY KEY,
    discussion_id UUID NOT NULL,
    user_id UUID NOT NULL,
    caption TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_discussion_reposts_discussion_id
        FOREIGN KEY (discussion_id) REFERENCES forum_discussions(discussion_id),
    CONSTRAINT fk_discussion_reposts_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_discussion_reposts_discussion_user UNIQUE (discussion_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_discussion_reposts_discussion_id ON discussion_reposts(discussion_id);
CREATE INDEX IF NOT EXISTS idx_discussion_reposts_user_id ON discussion_reposts(user_id);

--rollback DROP TABLE IF EXISTS discussion_reposts;
