--liquibase formatted sql

--changeset genlink:0017-create-interests-and-social-links
-- Interests back the "Choose Interests" onboarding step and personalise the home feed.
CREATE TABLE IF NOT EXISTS interests (
    interest_id UUID PRIMARY KEY,
    code VARCHAR(60) NOT NULL,
    name_en VARCHAR(80) NOT NULL,
    name_km VARCHAR(120) NOT NULL,
    category VARCHAR(60),
    is_active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT uk_interests_code UNIQUE (code)
);

CREATE TABLE IF NOT EXISTS user_interests (
    user_interest_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    interest_id UUID NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_user_interests_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT fk_user_interests_interest_id FOREIGN KEY (interest_id) REFERENCES interests(interest_id),
    CONSTRAINT uk_user_interests_user_interest UNIQUE (user_id, interest_id)
);

CREATE TABLE IF NOT EXISTS user_social_links (
    social_link_id UUID PRIMARY KEY,
    user_id UUID NOT NULL,
    platform VARCHAR(40) NOT NULL,
    url TEXT NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    updated_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_by VARCHAR(255) NOT NULL DEFAULT 'GenLinkKH',
    CONSTRAINT fk_user_social_links_user_id FOREIGN KEY (user_id) REFERENCES users(user_id),
    CONSTRAINT uk_user_social_links_user_platform UNIQUE (user_id, platform)
);

CREATE INDEX IF NOT EXISTS idx_user_interests_user_id ON user_interests(user_id);
CREATE INDEX IF NOT EXISTS idx_user_interests_interest_id ON user_interests(interest_id);
CREATE INDEX IF NOT EXISTS idx_user_social_links_user_id ON user_social_links(user_id);

--rollback DROP TABLE IF EXISTS user_social_links; DROP TABLE IF EXISTS user_interests; DROP TABLE IF EXISTS interests;

--changeset genlink:0017-seed-interests
-- Onboarding cannot render a chooser against an empty table, so ship a starter catalogue.
INSERT INTO interests (interest_id, code, name_en, name_km, category) VALUES
    (gen_random_uuid(), 'TECHNOLOGY',       'Technology',        'បច្ចេកវិទ្យា',        'CAREER'),
    (gen_random_uuid(), 'DESIGN',           'Design',            'ការរចនា',             'CAREER'),
    (gen_random_uuid(), 'BUSINESS',         'Business',          'អាជីវកម្ម',           'CAREER'),
    (gen_random_uuid(), 'ENTREPRENEURSHIP', 'Entrepreneurship',  'សហគ្រិនភាព',          'CAREER'),
    (gen_random_uuid(), 'FINANCE',          'Finance',           'ហិរញ្ញវត្ថុ',          'FINANCE'),
    (gen_random_uuid(), 'CAREER_GROWTH',    'Career Growth',     'ការរីកចម្រើនអាជីព',   'CAREER'),
    (gen_random_uuid(), 'EDUCATION',        'Education',         'ការអប់រំ',            'EDUCATION'),
    (gen_random_uuid(), 'LANGUAGE',         'Language Learning', 'ការរៀនភាសា',          'EDUCATION'),
    (gen_random_uuid(), 'SELF_GROWTH',      'Self Growth',       'ការអភិវឌ្ឍខ្លួនឯង',   'LIFESTYLE'),
    (gen_random_uuid(), 'HEALTH',           'Health',            'សុខភាព',              'LIFESTYLE'),
    (gen_random_uuid(), 'SPORTS',           'Sports',            'កីឡា',                'LIFESTYLE'),
    (gen_random_uuid(), 'MUSIC',            'Music',             'តន្ត្រី',              'ENTERTAINMENT'),
    (gen_random_uuid(), 'GAMING',           'Gaming',            'ហ្គេម',               'ENTERTAINMENT'),
    (gen_random_uuid(), 'ART',              'Art',               'សិល្បៈ',               'ENTERTAINMENT'),
    (gen_random_uuid(), 'TRAVEL',           'Travel',            'ការធ្វើដំណើរ',        'LIFESTYLE'),
    (gen_random_uuid(), 'VOLUNTEERING',     'Volunteering',      'ការស្ម័គ្រចិត្ត',      'COMMUNITY')
ON CONFLICT (code) DO NOTHING;

--rollback DELETE FROM interests;
