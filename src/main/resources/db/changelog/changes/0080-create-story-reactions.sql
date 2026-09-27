--liquibase formatted sql

--changeset genlink:0080-create-story-reactions
-- Persist one reaction per user and story so owner insights can show the real count.
CREATE TABLE IF NOT EXISTS story_reactions (
    story_reaction_id UUID PRIMARY KEY,
    story_id UUID NOT NULL,
    user_id UUID NOT NULL,
    reaction_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_story_reactions_story_id FOREIGN KEY (story_id) REFERENCES stories(story_id),
    CONSTRAINT fk_story_reactions_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_story_reactions_story_user UNIQUE (story_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_story_reactions_story_id ON story_reactions(story_id);

--rollback DROP TABLE IF EXISTS story_reactions;
