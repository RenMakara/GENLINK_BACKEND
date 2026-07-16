--liquibase formatted sql

--changeset genlink:0015-add-community-social-tables
CREATE TABLE IF NOT EXISTS communities (
    community_id UUID PRIMARY KEY,
    name VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL,
    description TEXT,
    category VARCHAR(60),
    cover_image_url TEXT,
    avatar_url TEXT,
    privacy VARCHAR(50) NOT NULL DEFAULT 'PUBLIC',
    created_by_user_id UUID NOT NULL,
    member_count INTEGER NOT NULL DEFAULT 0,
    post_count INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_communities_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES users(user_id),
    CONSTRAINT uk_communities_slug UNIQUE (slug)
);

CREATE TABLE IF NOT EXISTS community_members (
    community_member_id UUID PRIMARY KEY,
    community_id UUID NOT NULL,
    user_id UUID NOT NULL,
    role VARCHAR(50) NOT NULL DEFAULT 'MEMBER',
    status VARCHAR(50) NOT NULL DEFAULT 'ACTIVE',
    joined_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_community_members_community_id FOREIGN KEY (community_id) REFERENCES communities(community_id),
    CONSTRAINT fk_community_members_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_community_members_community_user UNIQUE (community_id, user_id)
);

CREATE TABLE IF NOT EXISTS follows (
    follow_id UUID PRIMARY KEY,
    follower_id UUID NOT NULL,
    following_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL DEFAULT 'ACCEPTED',
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_follows_follower_id FOREIGN KEY (follower_id) REFERENCES users(user_id),
    CONSTRAINT fk_follows_following_id FOREIGN KEY (following_id) REFERENCES users(user_id),
    CONSTRAINT uk_follows_follower_following UNIQUE (follower_id, following_id),
    CONSTRAINT chk_follows_no_self_follow CHECK (follower_id <> following_id)
);

CREATE TABLE IF NOT EXISTS hashtags (
    hashtag_id UUID PRIMARY KEY,
    tag VARCHAR(100) NOT NULL,
    usage_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT uk_hashtags_tag UNIQUE (tag)
);

CREATE TABLE IF NOT EXISTS post_hashtags (
    post_hashtag_id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    hashtag_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_post_hashtags_post_id FOREIGN KEY (post_id) REFERENCES posts(post_id),
    CONSTRAINT fk_post_hashtags_hashtag_id FOREIGN KEY (hashtag_id) REFERENCES hashtags(hashtag_id),
    CONSTRAINT uk_post_hashtags_post_hashtag UNIQUE (post_id, hashtag_id)
);

ALTER TABLE posts ADD COLUMN IF NOT EXISTS community_id UUID;
ALTER TABLE posts ADD CONSTRAINT fk_posts_community_id FOREIGN KEY (community_id) REFERENCES communities(community_id);

CREATE INDEX IF NOT EXISTS idx_communities_category ON communities(category);
CREATE INDEX IF NOT EXISTS idx_communities_created_by_user_id ON communities(created_by_user_id);
CREATE INDEX IF NOT EXISTS idx_follows_following_id ON follows(following_id);
CREATE INDEX IF NOT EXISTS idx_hashtags_usage_count ON hashtags(usage_count);
CREATE INDEX IF NOT EXISTS idx_posts_community_id ON posts(community_id);

--rollback DROP TABLE IF EXISTS post_hashtags; DROP TABLE IF EXISTS hashtags; DROP TABLE IF EXISTS follows; DROP TABLE IF EXISTS community_members; ALTER TABLE posts DROP COLUMN IF EXISTS community_id; DROP TABLE IF EXISTS communities;
