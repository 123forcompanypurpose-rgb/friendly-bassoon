CREATE TABLE chapter_viewers (
    chapter_id BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
    viewer_hash CHAR(64) NOT NULL,
    last_viewed_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (chapter_id, viewer_hash)
);

CREATE INDEX idx_chapter_viewers_last_viewed ON chapter_viewers (last_viewed_at);
