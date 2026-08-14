-- Stories: ephemeral media posts that expire 24h after creation, with per-viewer seen tracking.

CREATE TABLE IF NOT EXISTS stories (
    story_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    media_url TEXT NOT NULL,
    media_type VARCHAR(50) NOT NULL,
    caption TEXT,
    expires_at TIMESTAMP NOT NULL,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_stories_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

-- The feed reads active stories (not expired, not deleted) for a set of authors.
CREATE INDEX IF NOT EXISTS idx_stories_user_expires ON stories (user_id, expires_at);

CREATE TABLE IF NOT EXISTS story_views (
    view_id UUID PRIMARY KEY,
    story_id UUID NOT NULL,
    viewer_id UUID NOT NULL,
    viewed_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_story_views_story_id FOREIGN KEY (story_id) REFERENCES stories(story_id),
    CONSTRAINT fk_story_views_viewer_id FOREIGN KEY (viewer_id) REFERENCES users(user_id),
    CONSTRAINT uk_story_views_story_viewer UNIQUE (story_id, viewer_id)
);
