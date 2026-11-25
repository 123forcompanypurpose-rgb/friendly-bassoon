CREATE TABLE reports (
    id BIGSERIAL PRIMARY KEY,
    reporter_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
    target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('STORY', 'CHAPTER', 'COMMENT', 'USER', 'REVIEW')),
    target_id BIGINT NOT NULL,
    reason VARCHAR(20) NOT NULL CHECK (reason IN ('COPYRIGHT', 'SEXUAL_MINORS', 'HATE', 'SPAM', 'HARASSMENT', 'VIOLENCE', 'OTHER')),
    note VARCHAR(1000),
    priority SMALLINT NOT NULL DEFAULT 1,
    status VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'IN_REVIEW', 'ACTIONED', 'DISMISSED')),
    handled_by BIGINT REFERENCES users(id),
    resolution_note VARCHAR(1000),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_reports_queue ON reports(status, priority DESC, created_at);
CREATE INDEX idx_reports_reporter ON reports(reporter_id);

CREATE TABLE dmca_notices (
    id BIGSERIAL PRIMARY KEY,
    claimant_name VARCHAR(120) NOT NULL,
    claimant_email VARCHAR(160) NOT NULL,
    claimant_address VARCHAR(300),
    original_work_url VARCHAR(500),
    infringing_url VARCHAR(500) NOT NULL,
    statement_good_faith BOOLEAN NOT NULL,
    statement_accuracy BOOLEAN NOT NULL,
    signature VARCHAR(120) NOT NULL,
    report_id BIGINT REFERENCES reports(id),
    counter_notice_text TEXT,
    status VARCHAR(15) NOT NULL DEFAULT 'RECEIVED',
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_dmca_report ON dmca_notices(report_id);

CREATE TABLE moderation_actions (
    id BIGSERIAL PRIMARY KEY,
    moderator_id BIGINT NOT NULL REFERENCES users(id),
    action VARCHAR(20) NOT NULL,
    target_type VARCHAR(10) NOT NULL,
    target_id BIGINT NOT NULL,
    report_id BIGINT REFERENCES reports(id),
    reason VARCHAR(500),
    duration_days INT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_moderation_actions_moderator ON moderation_actions(moderator_id);
CREATE INDEX idx_moderation_actions_report ON moderation_actions(report_id);

CREATE TABLE audit_logs (
    id BIGSERIAL PRIMARY KEY,
    actor_id BIGINT,
    action VARCHAR(50) NOT NULL,
    entity VARCHAR(30),
    entity_id BIGINT,
    meta JSONB,
    ip_hash CHAR(64),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_audit_logs_actor ON audit_logs(actor_id);

CREATE TABLE featured_slots (
    id BIGSERIAL PRIMARY KEY,
    story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
    slot VARCHAR(20) NOT NULL,
    position INT NOT NULL DEFAULT 0,
    starts_at TIMESTAMPTZ,
    ends_at TIMESTAMPTZ
);
CREATE INDEX idx_featured_slots_story ON featured_slots(story_id);

CREATE TABLE announcements (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(150) NOT NULL,
    body VARCHAR(2000) NOT NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE shedlock (
    name VARCHAR(64) PRIMARY KEY,
    lock_until TIMESTAMPTZ NOT NULL,
    locked_at TIMESTAMPTZ NOT NULL,
    locked_by VARCHAR(255) NOT NULL
);
