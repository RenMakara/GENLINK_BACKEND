--liquibase formatted sql

--changeset genlink:0019-add-forum-reactions-and-reports
-- forum_discussions already carries reaction_count and report_count, but neither had
-- a table behind it. Nothing recorded WHO reacted or reported, so the counters could
-- only ever be free-floating integers: no idempotency, no reactedByMe, and no way to
-- stop one user inflating a count by tapping twice. Mirrors post_reactions.
CREATE TABLE IF NOT EXISTS forum_reactions (
    forum_reaction_id UUID PRIMARY KEY,
    discussion_id UUID NOT NULL,
    user_id UUID NOT NULL,
    reaction_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_forum_reactions_discussion_id FOREIGN KEY (discussion_id) REFERENCES forum_discussions(discussion_id),
    CONSTRAINT fk_forum_reactions_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_forum_reactions_discussion_user UNIQUE (discussion_id, user_id)
);

-- One report per user per discussion: the unique constraint is what makes
-- report_count mean "how many people flagged this" rather than "how many taps".
CREATE TABLE IF NOT EXISTS discussion_reports (
    discussion_report_id UUID PRIMARY KEY,
    discussion_id UUID NOT NULL,
    user_id UUID NOT NULL,
    reason VARCHAR(80) NOT NULL,
    details TEXT,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_discussion_reports_discussion_id FOREIGN KEY (discussion_id) REFERENCES forum_discussions(discussion_id),
    CONSTRAINT fk_discussion_reports_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_discussion_reports_discussion_user UNIQUE (discussion_id, user_id)
);

CREATE INDEX IF NOT EXISTS idx_forum_discussions_category ON forum_discussions(category);
CREATE INDEX IF NOT EXISTS idx_forum_reactions_discussion_id ON forum_reactions(discussion_id);
CREATE INDEX IF NOT EXISTS idx_discussion_reports_discussion_id ON discussion_reports(discussion_id);

--rollback DROP TABLE IF EXISTS discussion_reports; DROP TABLE IF EXISTS forum_reactions;
