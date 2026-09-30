--liquibase formatted sql

--changeset genlink:0028-add-forum-comment-threading
-- forum_comments was flat: no parent_comment_id, so "reply to comment" was impossible,
-- while post_comments already threaded one level (0009). The shared CommentCreateRequest
-- even carries parentCommentId, which the forum silently dropped. Mirrors post_comments.
-- Nullable: only a reply has a parent; a top-level comment has none.
ALTER TABLE forum_comments ADD COLUMN IF NOT EXISTS parent_comment_id UUID;
ALTER TABLE forum_comments ADD CONSTRAINT fk_forum_comments_parent_comment_id
    FOREIGN KEY (parent_comment_id) REFERENCES forum_comments(forum_comment_id);
CREATE INDEX IF NOT EXISTS idx_forum_comments_parent_comment_id ON forum_comments(parent_comment_id);

--rollback ALTER TABLE forum_comments DROP COLUMN IF EXISTS parent_comment_id;
