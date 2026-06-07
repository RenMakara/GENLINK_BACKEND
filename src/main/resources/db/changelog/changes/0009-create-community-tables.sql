--liquibase formatted sql

--changeset genlink:0009-create-community-tables
CREATE TABLE IF NOT EXISTS posts (
    post_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    content_text TEXT,
    image_url TEXT,
    post_type VARCHAR(50) NOT NULL,
    visibility VARCHAR(50) NOT NULL,
    language VARCHAR(20),
    like_count INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    share_count INTEGER NOT NULL DEFAULT 0,
    save_count INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_posts_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS post_comments (
    comment_id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    content TEXT NOT NULL,
    parent_comment_id UUID,
    language VARCHAR(20),
    like_count INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_post_comments_post_id FOREIGN KEY (post_id) REFERENCES posts(post_id),
    CONSTRAINT fk_post_comments_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_post_comments_parent_comment_id FOREIGN KEY (parent_comment_id) REFERENCES post_comments(comment_id)
);

CREATE TABLE IF NOT EXISTS post_reactions (
    reaction_id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    reaction_type VARCHAR(50) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_post_reactions_post_id FOREIGN KEY (post_id) REFERENCES posts(post_id),
    CONSTRAINT fk_post_reactions_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_post_reactions_post_user UNIQUE (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS saved_posts (
    saved_post_id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_saved_posts_post_id FOREIGN KEY (post_id) REFERENCES posts(post_id),
    CONSTRAINT fk_saved_posts_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_saved_posts_post_user UNIQUE (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS polls (
    poll_id UUID PRIMARY KEY,
    post_id UUID NOT NULL,
    question TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_polls_post_id FOREIGN KEY (post_id) REFERENCES posts(post_id)
);

CREATE TABLE IF NOT EXISTS poll_options (
    option_id UUID PRIMARY KEY,
    poll_id UUID NOT NULL,
    option_text TEXT NOT NULL,
    vote_count INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_poll_options_poll_id FOREIGN KEY (poll_id) REFERENCES polls(poll_id)
);

CREATE TABLE IF NOT EXISTS poll_votes (
    vote_id UUID PRIMARY KEY,
    poll_id UUID NOT NULL,
    option_id UUID NOT NULL,
    user_id UUID NOT NULL,
    voted_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_poll_votes_poll_id FOREIGN KEY (poll_id) REFERENCES polls(poll_id),
    CONSTRAINT fk_poll_votes_option_id FOREIGN KEY (option_id) REFERENCES poll_options(option_id),
    CONSTRAINT fk_poll_votes_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_poll_votes_poll_user UNIQUE (poll_id, user_id)
);

CREATE TABLE IF NOT EXISTS chat_rooms (
    chat_room_id UUID PRIMARY KEY,
    chat_type VARCHAR(50) NOT NULL,
    name VARCHAR(120),
    created_by_user_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_chat_rooms_created_by_user_id FOREIGN KEY (created_by_user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS chat_room_members (
    member_id UUID PRIMARY KEY,
    chat_room_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    muted_until TIMESTAMP,
    joined_at TIMESTAMP,
    left_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_chat_room_members_chat_room_id FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(chat_room_id),
    CONSTRAINT fk_chat_room_members_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_chat_room_members_room_user UNIQUE (chat_room_id, user_id)
);

CREATE TABLE IF NOT EXISTS chat_messages (
    message_id UUID PRIMARY KEY,
    chat_room_id UUID NOT NULL,
    sender_id UUID NOT NULL,
    message_type VARCHAR(50) NOT NULL,
    content TEXT,
    media_url TEXT,
    reply_to_message_id UUID,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_chat_messages_chat_room_id FOREIGN KEY (chat_room_id) REFERENCES chat_rooms(chat_room_id),
    CONSTRAINT fk_chat_messages_sender_id FOREIGN KEY (sender_id) REFERENCES users(user_id),
    CONSTRAINT fk_chat_messages_reply_to_message_id FOREIGN KEY (reply_to_message_id) REFERENCES chat_messages(message_id)
);

CREATE TABLE IF NOT EXISTS message_reads (
    message_read_id UUID PRIMARY KEY,
    message_id UUID NOT NULL,
    user_id UUID NOT NULL,
    read_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_message_reads_message_id FOREIGN KEY (message_id) REFERENCES chat_messages(message_id),
    CONSTRAINT fk_message_reads_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_message_reads_message_user UNIQUE (message_id, user_id)
);

CREATE TABLE IF NOT EXISTS forum_discussions (
    discussion_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    title VARCHAR(180) NOT NULL,
    content TEXT NOT NULL,
    category VARCHAR(80),
    is_anonymous BOOLEAN NOT NULL DEFAULT FALSE,
    language VARCHAR(20),
    reaction_count INTEGER NOT NULL DEFAULT 0,
    comment_count INTEGER NOT NULL DEFAULT 0,
    report_count INTEGER NOT NULL DEFAULT 0,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_forum_discussions_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS forum_comments (
    forum_comment_id UUID PRIMARY KEY,
    discussion_id UUID NOT NULL,
    user_id UUID NOT NULL,
    content TEXT NOT NULL,
    language VARCHAR(20),
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_forum_comments_discussion_id FOREIGN KEY (discussion_id) REFERENCES forum_discussions(discussion_id),
    CONSTRAINT fk_forum_comments_user_id FOREIGN KEY (user_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS events (
    event_id UUID PRIMARY KEY,
    organizer_id UUID NOT NULL,
    title VARCHAR(180) NOT NULL,
    description TEXT,
    category VARCHAR(80),
    cover_image_url TEXT,
    date_time TIMESTAMP NOT NULL,
    location_name VARCHAR(180),
    latitude NUMERIC(10,7),
    longitude NUMERIC(10,7),
    attendee_limit INTEGER,
    attendee_count INTEGER NOT NULL DEFAULT 0,
    status VARCHAR(50) NOT NULL,
    language VARCHAR(20),
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_events_organizer_id FOREIGN KEY (organizer_id) REFERENCES users(user_id)
);

CREATE TABLE IF NOT EXISTS event_rsvps (
    rsvp_id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    user_id UUID NOT NULL,
    status VARCHAR(50) NOT NULL,
    checked_in BOOLEAN NOT NULL DEFAULT FALSE,
    qr_code VARCHAR(255),
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_event_rsvps_event_id FOREIGN KEY (event_id) REFERENCES events(event_id),
    CONSTRAINT fk_event_rsvps_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_event_rsvps_event_user UNIQUE (event_id, user_id)
);

CREATE TABLE IF NOT EXISTS opportunities (
    opportunity_id UUID PRIMARY KEY,
    title VARCHAR(180) NOT NULL,
    organization VARCHAR(180),
    type VARCHAR(80),
    description TEXT,
    deadline DATE,
    location VARCHAR(120),
    apply_link TEXT,
    deleted_at TIMESTAMP,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH'
);

--rollback DROP TABLE IF EXISTS opportunities; DROP TABLE IF EXISTS event_rsvps; DROP TABLE IF EXISTS events; DROP TABLE IF EXISTS forum_comments; DROP TABLE IF EXISTS forum_discussions; DROP TABLE IF EXISTS message_reads; DROP TABLE IF EXISTS chat_messages; DROP TABLE IF EXISTS chat_room_members; DROP TABLE IF EXISTS chat_rooms; DROP TABLE IF EXISTS poll_votes; DROP TABLE IF EXISTS poll_options; DROP TABLE IF EXISTS polls; DROP TABLE IF EXISTS saved_posts; DROP TABLE IF EXISTS post_reactions; DROP TABLE IF EXISTS post_comments; DROP TABLE IF EXISTS posts;
