CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
    id BIGSERIAL PRIMARY KEY,
    email CITEXT NOT NULL UNIQUE,
    username CITEXT NOT NULL UNIQUE,
    password_hash VARCHAR(100),
    display_name VARCHAR(60) NOT NULL,
    bio VARCHAR(500),
    avatar_url VARCHAR(500),
    social_links JSONB NOT NULL DEFAULT '{}'::jsonb,
    role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER', 'MODERATOR', 'ADMIN')),
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'SUSPENDED', 'BANNED', 'DELETED')),
    suspended_until TIMESTAMPTZ,
    email_verified BOOLEAN NOT NULL DEFAULT FALSE,
    terms_accepted_at TIMESTAMPTZ,
    google_sub VARCHAR(64) UNIQUE,
    birth_date DATE,
    show_mature BOOLEAN NOT NULL DEFAULT FALSE,
    locale VARCHAR(10) NOT NULL DEFAULT 'en',
    country CHAR(2),
    failed_login_count INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    last_seen_at TIMESTAMPTZ,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_users_username_trgm ON users USING gin (username gin_trgm_ops);

CREATE TABLE refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    token_hash CHAR(64) NOT NULL UNIQUE,
    family_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    replaced_by BIGINT REFERENCES refresh_tokens(id),
    user_agent VARCHAR(255),
    ip_hash CHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_refresh_tokens_user ON refresh_tokens(user_id);
CREATE INDEX idx_refresh_tokens_family ON refresh_tokens(family_id);

CREATE TABLE email_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(20) NOT NULL CHECK (type IN ('VERIFY_EMAIL', 'RESET_PASSWORD')),
    token_hash CHAR(64) NOT NULL UNIQUE,
    expires_at TIMESTAMPTZ NOT NULL,
    used_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_email_tokens_user_type ON email_tokens(user_id, type);

CREATE TABLE login_attempts (
    identity_hash CHAR(64) NOT NULL,
    ip_hash CHAR(64) NOT NULL,
    failed_attempts INT NOT NULL DEFAULT 0,
    locked_until TIMESTAMPTZ,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (identity_hash, ip_hash)
);

CREATE TABLE genres (
    id SMALLSERIAL PRIMARY KEY,
    name VARCHAR(50) NOT NULL UNIQUE,
    slug VARCHAR(60) NOT NULL UNIQUE,
    sort_order INT NOT NULL DEFAULT 0,
    active BOOLEAN NOT NULL DEFAULT TRUE
);

CREATE TABLE tags (
    id BIGSERIAL PRIMARY KEY,
    name VARCHAR(40) NOT NULL UNIQUE,
    slug VARCHAR(50) NOT NULL UNIQUE,
    use_count INT NOT NULL DEFAULT 0
);

CREATE TABLE stories (
    id BIGSERIAL PRIMARY KEY,
    author_id BIGINT NOT NULL REFERENCES users(id),
    title VARCHAR(120) NOT NULL,
    slug VARCHAR(140) NOT NULL,
    blurb VARCHAR(2000) NOT NULL DEFAULT '',
    cover_url VARCHAR(500),
    genre_id SMALLINT NOT NULL REFERENCES genres(id),
    language VARCHAR(10) NOT NULL DEFAULT 'en',
    maturity VARCHAR(10) NOT NULL DEFAULT 'EVERYONE' CHECK (maturity IN ('EVERYONE', 'TEEN', 'MATURE')),
    status VARCHAR(10) NOT NULL DEFAULT 'ONGOING' CHECK (status IN ('ONGOING', 'COMPLETED', 'HIATUS')),
    visibility VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (visibility IN ('DRAFT', 'PUBLISHED', 'UNLISTED', 'REMOVED')),
    is_featured BOOLEAN NOT NULL DEFAULT FALSE,
    chapter_count INT NOT NULL DEFAULT 0,
    word_count INT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    vote_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    library_count INT NOT NULL DEFAULT 0,
    rating_sum INT NOT NULL DEFAULT 0,
    rating_count INT NOT NULL DEFAULT 0,
    published_at TIMESTAMPTZ,
    last_chapter_at TIMESTAMPTZ,
    removed_reason VARCHAR(500),
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    search_vector TSVECTOR
);
CREATE INDEX idx_stories_author ON stories(author_id);
CREATE INDEX idx_stories_browse ON stories(visibility, genre_id, last_chapter_at DESC) WHERE deleted_at IS NULL;
CREATE INDEX idx_stories_published ON stories(published_at DESC) WHERE visibility = 'PUBLISHED';
CREATE INDEX idx_stories_title_trgm ON stories USING gin (title gin_trgm_ops);
CREATE INDEX idx_stories_search ON stories USING gin (search_vector);

CREATE TABLE story_tags (
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    tag_id BIGINT NOT NULL REFERENCES tags(id),
    PRIMARY KEY (story_id, tag_id)
);
CREATE INDEX idx_story_tags_tag ON story_tags(tag_id);

CREATE TABLE chapters (
    id BIGSERIAL PRIMARY KEY,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    number INT NOT NULL,
    title VARCHAR(150) NOT NULL,
    content_html TEXT NOT NULL DEFAULT '',
    author_note VARCHAR(2000),
    word_count INT NOT NULL DEFAULT 0,
    status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'REMOVED')),
    publish_at TIMESTAMPTZ,
    published_at TIMESTAMPTZ,
    is_premium BOOLEAN NOT NULL DEFAULT FALSE,
    coin_price INT NOT NULL DEFAULT 0,
    view_count BIGINT NOT NULL DEFAULT 0,
    vote_count INT NOT NULL DEFAULT 0,
    comment_count INT NOT NULL DEFAULT 0,
    version INT NOT NULL DEFAULT 0,
    deleted_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_chapters_story_number UNIQUE (story_id, number) DEFERRABLE INITIALLY DEFERRED
);
CREATE INDEX idx_chapters_story_status ON chapters(story_id, status, number);
CREATE INDEX idx_chapters_scheduled ON chapters(publish_at) WHERE status = 'SCHEDULED';

CREATE TABLE chapter_drafts (
    chapter_id BIGINT PRIMARY KEY REFERENCES chapters(id) ON DELETE CASCADE,
    title VARCHAR(150) NOT NULL,
    content_html TEXT NOT NULL DEFAULT '',
    author_note VARCHAR(2000),
    base_version INT NOT NULL DEFAULT 0,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
