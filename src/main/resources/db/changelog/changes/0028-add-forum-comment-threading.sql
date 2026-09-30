--liquibase formatted sql

--changeset genlink:0028-add-forum-comment-threading
-- forum_comments was flat: no parent_comment_id.
ALTER TABLE forum_comments ADD COLUMN IF NOT EXISTS parent_comment_id UUID;
ALTER TABLE forum_comments ADD CONSTRAINT fk_forum_comments_parent_comment_id
    FOREIGN KEY (parent_comment_id) REFERENCES forum_comments(forum_comment_id);
CREATE INDEX IF NOT EXISTS idx_forum_comments_parent_comment_id ON forum_comments(parent_comment_id);

--rollback ALTER TABLE forum_comments DROP COLUMN IF EXISTS parent_comment_id;
