--liquibase formatted sql

--changeset genlink:0030-add-post-comment-likes
-- post_comments already had like_count (0009) but nothing incremented it: there was no
-- reaction table, so post-comment likes were impossible while forum-comment likes now work
-- (0029). This adds the reaction table. One like per user per comment (distinct people, not
-- taps). Mirrors post_reactions and forum_comment_reactions.
CREATE TABLE IF NOT EXISTS post_comment_reactions (
    post_comment_reaction_id UUID PRIMARY KEY,
    comment_id UUID NOT NULL,
    user_id UUID NOT NULL,
    reaction_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_post_comment_reactions_comment_id FOREIGN KEY (comment_id) REFERENCES post_comments(comment_id),
    CONSTRAINT fk_post_comment_reactions_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_post_comment_reactions_comment_user UNIQUE (comment_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_post_comment_reactions_comment_id ON post_comment_reactions(comment_id);

--rollback DROP TABLE IF EXISTS post_comment_reactions;
