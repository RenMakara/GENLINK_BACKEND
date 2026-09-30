--liquibase formatted sql

--changeset genlink:0027-add-post-reports
-- Posts had no reporting at all - no report_count and no table - so a community moderator had nothing to review.
ALTER TABLE posts ADD COLUMN IF NOT EXISTS report_count INTEGER NOT NULL DEFAULT 0;

CREATE TABLE IF NOT EXISTS post_reports (
    post_report_id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    reason VARCHAR(80) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_post_reports_post_id FOREIGN KEY (post_id) REFERENCES posts(post_id),
    CONSTRAINT fk_post_reports_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_post_reports_post_user UNIQUE (post_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_post_reports_post_id ON post_reports(post_id);
CREATE INDEX IF NOT EXISTS idx_posts_report_count ON posts(report_count);

--rollback DROP TABLE IF EXISTS post_reports; ALTER TABLE posts DROP COLUMN IF EXISTS report_count;
