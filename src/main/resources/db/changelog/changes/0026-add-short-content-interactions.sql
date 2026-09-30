--liquibase formatted sql

--changeset genlink:0026-add-short-content-interactions
-- Per-user like/save tracking for short content, so likes/saves are idempotent and the
-- feed can show a liked/saved flag - the counters on short_contents alone cannot do that.
-- No seed: short_contents.creator_id is NOT NULL and there is no seeded user, so content
-- is created at runtime via POST /short-content (metadata only, no media upload).
CREATE TABLE IF NOT EXISTS short_content_likes (
    like_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    short_content_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_short_content_likes_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_short_content_likes_content_id FOREIGN KEY (short_content_id) REFERENCES short_contents(short_content_id),
    CONSTRAINT uk_short_content_likes_user_content UNIQUE (user_id, short_content_id)
);

CREATE TABLE IF NOT EXISTS short_content_saves (
    save_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    short_content_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_short_content_saves_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_short_content_saves_content_id FOREIGN KEY (short_content_id) REFERENCES short_contents(short_content_id),
    CONSTRAINT uk_short_content_saves_user_content UNIQUE (user_id, short_content_id)
);

CREATE INDEX IF NOT EXISTS idx_short_content_likes_content_id ON short_content_likes(short_content_id);
CREATE INDEX IF NOT EXISTS idx_short_content_saves_user_id ON short_content_saves(user_id);

--rollback DROP TABLE IF EXISTS short_content_saves; DROP TABLE IF EXISTS short_content_likes;
