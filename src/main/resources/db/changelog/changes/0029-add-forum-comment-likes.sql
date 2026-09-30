--liquibase formatted sql

--changeset genlink:0029-add-forum-comment-likes
-- forum_comments had no like_count and no reaction table, so CommentResponse.likeCount was
-- hard-coded 0 - comment likes were impossible. Mirrors forum_reactions (0019). One like per
-- user per comment, so like_count means "distinct people", not "how many taps".
ALTER TABLE forum_comments ADD COLUMN IF NOT EXISTS like_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS forum_comment_reactions (
    forum_comment_reaction_id UUID PRIMARY KEY,
    forum_comment_id UUID NOT NULL,
    user_id UUID NOT NULL,
    reaction_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_forum_comment_reactions_comment_id FOREIGN KEY (forum_comment_id) REFERENCES forum_comments(forum_comment_id),
    CONSTRAINT fk_forum_comment_reactions_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_forum_comment_reactions_comment_user UNIQUE (forum_comment_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_forum_comment_reactions_comment_id ON forum_comment_reactions(forum_comment_id);

--rollback DROP TABLE IF EXISTS forum_comment_reactions; ALTER TABLE forum_comments DROP COLUMN IF EXISTS like_count;
