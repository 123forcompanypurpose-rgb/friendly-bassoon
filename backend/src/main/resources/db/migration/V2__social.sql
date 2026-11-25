CREATE TABLE reading_progress (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    chapter_id BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    scroll_pct SMALLINT NOT NULL DEFAULT 0 CHECK (scroll_pct BETWEEN 0 AND 100),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, story_id)
);
CREATE INDEX idx_reading_progress_user_updated ON reading_progress(user_id, updated_at DESC);
CREATE INDEX idx_reading_progress_story ON reading_progress(story_id);
CREATE INDEX idx_reading_progress_chapter ON reading_progress(chapter_id);

CREATE TABLE library_items (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    notify_new_chapter BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, story_id)
);
CREATE INDEX idx_library_items_story ON library_items(story_id);

CREATE TABLE reading_lists (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    name VARCHAR(80) NOT NULL,
    description VARCHAR(500),
    is_public BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_reading_lists_user ON reading_lists(user_id);

CREATE TABLE reading_list_items (
    list_id BIGINT NOT NULL REFERENCES reading_lists(id) ON DELETE CASCADE,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    position INT NOT NULL DEFAULT 0,
    PRIMARY KEY (list_id, story_id)
);
CREATE INDEX idx_reading_list_items_story ON reading_list_items(story_id);

CREATE TABLE follows (
    follower_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    followee_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (follower_id, followee_id),
    CHECK (follower_id <> followee_id)
);
CREATE INDEX idx_follows_followee ON follows(followee_id);

CREATE TABLE blocks (
    blocker_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    blocked_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (blocker_id, blocked_id),
    CHECK (blocker_id <> blocked_id)
);
CREATE INDEX idx_blocks_blocked ON blocks(blocked_id);

CREATE TABLE story_votes (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, story_id)
);
CREATE INDEX idx_story_votes_story ON story_votes(story_id);

CREATE TABLE chapter_votes (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    chapter_id BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (user_id, chapter_id)
);
CREATE INDEX idx_chapter_votes_chapter ON chapter_votes(chapter_id);

CREATE TABLE reviews (
    id BIGSERIAL PRIMARY KEY,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
    body VARCHAR(3000),
    status VARCHAR(10) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE', 'HIDDEN')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (story_id, user_id)
);
CREATE INDEX idx_reviews_story_created ON reviews(story_id, created_at DESC);
CREATE INDEX idx_reviews_user ON reviews(user_id);

CREATE TABLE comments (
    id BIGSERIAL PRIMARY KEY,
    chapter_id BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    user_id BIGINT NOT NULL REFERENCES users(id),
    parent_id BIGINT REFERENCES comments(id) ON DELETE CASCADE,
    body VARCHAR(2000) NOT NULL,
    like_count INT NOT NULL DEFAULT 0,
    reply_count INT NOT NULL DEFAULT 0,
    is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
    status VARCHAR(10) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE', 'HIDDEN', 'DELETED')),
    edited_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_comments_chapter_parent_created ON comments(chapter_id, parent_id, created_at DESC);
CREATE INDEX idx_comments_parent ON comments(parent_id);
CREATE INDEX idx_comments_story ON comments(story_id);
CREATE INDEX idx_comments_user ON comments(user_id);

CREATE TABLE comment_likes (
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    comment_id BIGINT NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
    PRIMARY KEY (user_id, comment_id)
);
CREATE INDEX idx_comment_likes_comment ON comment_likes(comment_id);

CREATE TABLE notifications (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
    type VARCHAR(30) NOT NULL,
    actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    story_id BIGINT REFERENCES stories(id) ON DELETE CASCADE,
    chapter_id BIGINT REFERENCES chapters(id) ON DELETE CASCADE,
    comment_id BIGINT REFERENCES comments(id) ON DELETE CASCADE,
    message VARCHAR(300),
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notifications_user_unread ON notifications(user_id, read_at, created_at DESC);
CREATE INDEX idx_notifications_actor ON notifications(actor_id);

CREATE TABLE notification_prefs (
    user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
    email_new_chapter BOOLEAN NOT NULL DEFAULT FALSE,
    email_replies BOOLEAN NOT NULL DEFAULT TRUE,
    email_marketing BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE story_rank_snapshots (
    period VARCHAR(10) NOT NULL CHECK (period IN ('DAILY', 'WEEKLY', 'MONTHLY', 'ALLTIME')),
    genre_id SMALLINT REFERENCES genres(id) ON DELETE CASCADE,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    rank_pos INT NOT NULL,
    score DOUBLE PRECISION NOT NULL,
    computed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_story_rank_lookup ON story_rank_snapshots(period, genre_id, rank_pos);

CREATE TABLE daily_story_stats (
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    day DATE NOT NULL,
    views INT NOT NULL DEFAULT 0,
    votes INT NOT NULL DEFAULT 0,
    comments INT NOT NULL DEFAULT 0,
    library_adds INT NOT NULL DEFAULT 0,
    PRIMARY KEY (story_id, day)
);
