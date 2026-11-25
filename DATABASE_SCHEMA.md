# DATABASE SCHEMA (PostgreSQL 16, Flyway)

Conventions: `snake_case`; `id BIGSERIAL` PK (use `UUID` for tokens); `created_at/updated_at TIMESTAMPTZ NOT NULL DEFAULT now()`; soft delete via `deleted_at`; enums as `VARCHAR` + CHECK (easier for Flyway than PG enums); all FKs indexed.
Split into migrations: `V1__core.sql`, `V2__social.sql`, `V3__moderation.sql`, `V4__search.sql`, `V12__payments.sql` (Phase 12).

## V1 — Core
```sql
CREATE EXTENSION IF NOT EXISTS pg_trgm;
CREATE EXTENSION IF NOT EXISTS citext;

CREATE TABLE users (
  id BIGSERIAL PRIMARY KEY,
  email CITEXT NOT NULL UNIQUE,
  username CITEXT NOT NULL UNIQUE,
  password_hash VARCHAR(100),                       -- null for Google-only accounts
  display_name VARCHAR(60) NOT NULL,
  bio VARCHAR(500),
  avatar_url VARCHAR(500),
  role VARCHAR(20) NOT NULL DEFAULT 'USER' CHECK (role IN ('USER','MODERATOR','ADMIN')),
  status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE','SUSPENDED','BANNED','DELETED')),
  suspended_until TIMESTAMPTZ,
  email_verified BOOLEAN NOT NULL DEFAULT FALSE,
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
  replaced_by BIGINT,
  user_agent VARCHAR(255), ip_hash CHAR(64),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rt_user ON refresh_tokens(user_id);

CREATE TABLE email_tokens (                          -- verify email / reset password
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  type VARCHAR(20) NOT NULL CHECK (type IN ('VERIFY_EMAIL','RESET_PASSWORD')),
  token_hash CHAR(64) NOT NULL UNIQUE,
  expires_at TIMESTAMPTZ NOT NULL,
  used_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE genres (
  id SMALLSERIAL PRIMARY KEY,
  name VARCHAR(50) NOT NULL UNIQUE,
  slug VARCHAR(60) NOT NULL UNIQUE,
  sort_order INT NOT NULL DEFAULT 0,
  active BOOLEAN NOT NULL DEFAULT TRUE
);
-- seed: Romance, Fantasy, Werewolf, Billionaire/CEO, Mafia, Urban, Sci-Fi, Mystery/Thriller,
--       Horror, Historical, Young Adult, Teen Fiction, Fan Fiction, Action, Adventure, Comedy, Drama, LGBTQ+, Slice of Life, Poetry, Non-Fiction

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
  maturity VARCHAR(10) NOT NULL DEFAULT 'EVERYONE' CHECK (maturity IN ('EVERYONE','TEEN','MATURE')),
  status VARCHAR(10) NOT NULL DEFAULT 'ONGOING' CHECK (status IN ('ONGOING','COMPLETED','HIATUS')),
  visibility VARCHAR(10) NOT NULL DEFAULT 'DRAFT' CHECK (visibility IN ('DRAFT','PUBLISHED','UNLISTED','REMOVED')),
  is_featured BOOLEAN NOT NULL DEFAULT FALSE,
  chapter_count INT NOT NULL DEFAULT 0,          -- published chapters
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
CREATE INDEX idx_stories_published ON stories(published_at DESC) WHERE visibility='PUBLISHED';
CREATE INDEX idx_stories_title_trgm ON stories USING gin (title gin_trgm_ops);
CREATE INDEX idx_stories_search ON stories USING gin (search_vector);
-- search_vector maintained by trigger (title A, tags/author B, blurb C) — see V4

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
  content_html TEXT NOT NULL DEFAULT '',           -- sanitized published content
  author_note VARCHAR(2000),
  word_count INT NOT NULL DEFAULT 0,
  status VARCHAR(12) NOT NULL DEFAULT 'DRAFT' CHECK (status IN ('DRAFT','SCHEDULED','PUBLISHED','REMOVED')),
  publish_at TIMESTAMPTZ,
  published_at TIMESTAMPTZ,
  is_premium BOOLEAN NOT NULL DEFAULT FALSE,       -- Phase 12
  coin_price INT NOT NULL DEFAULT 0,               -- Phase 12
  view_count BIGINT NOT NULL DEFAULT 0,
  vote_count INT NOT NULL DEFAULT 0,
  comment_count INT NOT NULL DEFAULT 0,
  version INT NOT NULL DEFAULT 0,                  -- optimistic locking
  deleted_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (story_id, number) DEFERRABLE INITIALLY DEFERRED  -- allows reorder in one tx
);
CREATE INDEX idx_chapters_story_status ON chapters(story_id, status, number);
CREATE INDEX idx_chapters_scheduled ON chapters(publish_at) WHERE status='SCHEDULED';

CREATE TABLE chapter_drafts (                      -- working copy while editing
  chapter_id BIGINT PRIMARY KEY REFERENCES chapters(id) ON DELETE CASCADE,
  title VARCHAR(150) NOT NULL,
  content_html TEXT NOT NULL DEFAULT '',
  author_note VARCHAR(2000),
  base_version INT NOT NULL DEFAULT 0,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
```

## V2 — Reading, library, social, notifications
```sql
CREATE TABLE reading_progress (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  chapter_id BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
  scroll_pct SMALLINT NOT NULL DEFAULT 0 CHECK (scroll_pct BETWEEN 0 AND 100),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, story_id)
);
CREATE INDEX idx_rp_user_updated ON reading_progress(user_id, updated_at DESC);

CREATE TABLE library_items (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  notify_new_chapter BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, story_id)
);
CREATE INDEX idx_library_story ON library_items(story_id);

CREATE TABLE reading_lists (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  name VARCHAR(80) NOT NULL, description VARCHAR(500),
  is_public BOOLEAN NOT NULL DEFAULT TRUE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE reading_list_items (
  list_id BIGINT NOT NULL REFERENCES reading_lists(id) ON DELETE CASCADE,
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  position INT NOT NULL DEFAULT 0,
  PRIMARY KEY (list_id, story_id)
);

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
  PRIMARY KEY (blocker_id, blocked_id)
);

CREATE TABLE story_votes (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, story_id)
);
CREATE TABLE chapter_votes (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  chapter_id BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  PRIMARY KEY (user_id, chapter_id)
);

CREATE TABLE reviews (
  id BIGSERIAL PRIMARY KEY,
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  rating SMALLINT NOT NULL CHECK (rating BETWEEN 1 AND 5),
  body VARCHAR(3000),
  status VARCHAR(10) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE','HIDDEN')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  UNIQUE (story_id, user_id)
);

CREATE TABLE comments (
  id BIGSERIAL PRIMARY KEY,
  chapter_id BIGINT NOT NULL REFERENCES chapters(id) ON DELETE CASCADE,
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,  -- denormalized for fast owner moderation
  user_id BIGINT NOT NULL REFERENCES users(id),
  parent_id BIGINT REFERENCES comments(id) ON DELETE CASCADE,        -- one level only (enforce in service)
  body VARCHAR(2000) NOT NULL,
  like_count INT NOT NULL DEFAULT 0,
  reply_count INT NOT NULL DEFAULT 0,
  is_pinned BOOLEAN NOT NULL DEFAULT FALSE,
  status VARCHAR(10) NOT NULL DEFAULT 'VISIBLE' CHECK (status IN ('VISIBLE','HIDDEN','DELETED')),
  edited_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_comments_chapter ON comments(chapter_id, parent_id, created_at DESC);
CREATE INDEX idx_comments_parent ON comments(parent_id);
CREATE TABLE comment_likes (
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,
  comment_id BIGINT NOT NULL REFERENCES comments(id) ON DELETE CASCADE,
  PRIMARY KEY (user_id, comment_id)
);

CREATE TABLE notifications (
  id BIGSERIAL PRIMARY KEY,
  user_id BIGINT NOT NULL REFERENCES users(id) ON DELETE CASCADE,   -- recipient
  type VARCHAR(30) NOT NULL,   -- NEW_CHAPTER, COMMENT_REPLY, CHAPTER_COMMENT, NEW_FOLLOWER, VOTE_MILESTONE, MODERATION, ANNOUNCEMENT
  actor_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  story_id BIGINT REFERENCES stories(id) ON DELETE CASCADE,
  chapter_id BIGINT REFERENCES chapters(id) ON DELETE CASCADE,
  comment_id BIGINT REFERENCES comments(id) ON DELETE CASCADE,
  message VARCHAR(300),
  read_at TIMESTAMPTZ,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_notif_user ON notifications(user_id, read_at, created_at DESC);

CREATE TABLE notification_prefs (
  user_id BIGINT PRIMARY KEY REFERENCES users(id) ON DELETE CASCADE,
  email_new_chapter BOOLEAN NOT NULL DEFAULT FALSE,
  email_replies BOOLEAN NOT NULL DEFAULT TRUE,
  email_marketing BOOLEAN NOT NULL DEFAULT FALSE
);

CREATE TABLE story_rank_snapshots (
  period VARCHAR(10) NOT NULL CHECK (period IN ('DAILY','WEEKLY','MONTHLY','ALLTIME')),
  genre_id SMALLINT,               -- NULL = overall
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  rank_pos INT NOT NULL,
  score DOUBLE PRECISION NOT NULL,
  computed_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_rank_lookup ON story_rank_snapshots(period, genre_id, rank_pos);

CREATE TABLE daily_story_stats (     -- for analytics charts
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  day DATE NOT NULL,
  views INT NOT NULL DEFAULT 0, votes INT NOT NULL DEFAULT 0,
  comments INT NOT NULL DEFAULT 0, library_adds INT NOT NULL DEFAULT 0,
  PRIMARY KEY (story_id, day)
);
```

## V3 — Moderation & ops
```sql
CREATE TABLE reports (
  id BIGSERIAL PRIMARY KEY,
  reporter_id BIGINT REFERENCES users(id) ON DELETE SET NULL,
  target_type VARCHAR(10) NOT NULL CHECK (target_type IN ('STORY','CHAPTER','COMMENT','USER','REVIEW')),
  target_id BIGINT NOT NULL,
  reason VARCHAR(20) NOT NULL CHECK (reason IN ('COPYRIGHT','SEXUAL_MINORS','HATE','SPAM','HARASSMENT','VIOLENCE','OTHER')),
  note VARCHAR(1000),
  priority SMALLINT NOT NULL DEFAULT 1,         -- DMCA / minors = 3
  status VARCHAR(12) NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN','IN_REVIEW','ACTIONED','DISMISSED')),
  handled_by BIGINT REFERENCES users(id),
  resolution_note VARCHAR(1000),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  resolved_at TIMESTAMPTZ
);
CREATE INDEX idx_reports_queue ON reports(status, priority DESC, created_at);

CREATE TABLE dmca_notices (
  id BIGSERIAL PRIMARY KEY,
  claimant_name VARCHAR(120) NOT NULL, claimant_email VARCHAR(160) NOT NULL, claimant_address VARCHAR(300),
  original_work_url VARCHAR(500), infringing_url VARCHAR(500) NOT NULL,
  statement_good_faith BOOLEAN NOT NULL, statement_accuracy BOOLEAN NOT NULL, signature VARCHAR(120) NOT NULL,
  report_id BIGINT REFERENCES reports(id),
  counter_notice_text TEXT,
  status VARCHAR(15) NOT NULL DEFAULT 'RECEIVED',
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE moderation_actions (
  id BIGSERIAL PRIMARY KEY,
  moderator_id BIGINT NOT NULL REFERENCES users(id),
  action VARCHAR(20) NOT NULL,      -- REMOVE_CONTENT, RESTORE, WARN, SUSPEND, BAN, UNBAN, DISMISS
  target_type VARCHAR(10) NOT NULL, target_id BIGINT NOT NULL,
  report_id BIGINT REFERENCES reports(id),
  reason VARCHAR(500), duration_days INT,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE audit_logs (
  id BIGSERIAL PRIMARY KEY,
  actor_id BIGINT, action VARCHAR(50) NOT NULL, entity VARCHAR(30), entity_id BIGINT,
  meta JSONB, ip_hash CHAR(64), created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE featured_slots (
  id BIGSERIAL PRIMARY KEY,
  story_id BIGINT NOT NULL REFERENCES stories(id) ON DELETE CASCADE,
  slot VARCHAR(20) NOT NULL,  -- HOME_HERO, EDITORS_PICK, GENRE_<id>
  position INT NOT NULL DEFAULT 0,
  starts_at TIMESTAMPTZ, ends_at TIMESTAMPTZ
);

CREATE TABLE announcements (
  id BIGSERIAL PRIMARY KEY, title VARCHAR(150) NOT NULL, body VARCHAR(2000) NOT NULL,
  active BOOLEAN NOT NULL DEFAULT TRUE, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE shedlock (               -- ShedLock JDBC
  name VARCHAR(64) PRIMARY KEY, lock_until TIMESTAMPTZ NOT NULL,
  locked_at TIMESTAMPTZ NOT NULL, locked_by VARCHAR(255) NOT NULL
);
```

## V4 — Search trigger (sketch)
```sql
CREATE OR REPLACE FUNCTION stories_search_update() RETURNS trigger AS $$
BEGIN
  NEW.search_vector :=
    setweight(to_tsvector('simple', coalesce(NEW.title,'')), 'A') ||
    setweight(to_tsvector('simple', coalesce(NEW.blurb,'')), 'C');
  RETURN NEW;
END $$ LANGUAGE plpgsql;
CREATE TRIGGER trg_stories_search BEFORE INSERT OR UPDATE OF title, blurb ON stories
FOR EACH ROW EXECUTE FUNCTION stories_search_update();
-- Use 'simple' config so non-English (Hindi etc.) works. Tags and author name are matched via trigram/joins in the query.
```

## V12 — Payments (Phase 12)
```sql
CREATE TABLE wallets (
  user_id BIGINT PRIMARY KEY REFERENCES users(id),
  balance INT NOT NULL DEFAULT 0 CHECK (balance >= 0),    -- cached; ledger is source of truth
  updated_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE coin_packages (
  id SMALLSERIAL PRIMARY KEY, coins INT NOT NULL, bonus_coins INT NOT NULL DEFAULT 0,
  price_inr_paise INT NOT NULL, price_usd_cents INT NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE payments (
  id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
  provider VARCHAR(10) NOT NULL CHECK (provider IN ('RAZORPAY','STRIPE')),
  provider_order_id VARCHAR(100) NOT NULL, provider_payment_id VARCHAR(100),
  package_id SMALLINT REFERENCES coin_packages(id),
  amount_minor INT NOT NULL, currency CHAR(3) NOT NULL, coins INT NOT NULL,
  status VARCHAR(12) NOT NULL DEFAULT 'CREATED' CHECK (status IN ('CREATED','PAID','FAILED','REFUNDED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), paid_at TIMESTAMPTZ,
  UNIQUE (provider, provider_order_id)
);
CREATE TABLE payment_events (        -- webhook idempotency
  provider VARCHAR(10) NOT NULL, event_id VARCHAR(120) NOT NULL, payload JSONB,
  processed_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (provider, event_id)
);
CREATE TABLE coin_transactions (     -- append-only ledger
  id BIGSERIAL PRIMARY KEY, user_id BIGINT NOT NULL REFERENCES users(id),
  type VARCHAR(15) NOT NULL CHECK (type IN ('PURCHASE','UNLOCK','BONUS','REFUND','ADJUSTMENT','CHECKIN')),
  amount INT NOT NULL,                       -- + credit, - debit
  balance_after INT NOT NULL,
  ref_type VARCHAR(15), ref_id BIGINT, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX idx_coin_tx_user ON coin_transactions(user_id, created_at DESC);
CREATE TABLE chapter_unlocks (
  user_id BIGINT NOT NULL REFERENCES users(id), chapter_id BIGINT NOT NULL REFERENCES chapters(id),
  story_id BIGINT NOT NULL REFERENCES stories(id), coins_spent INT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), PRIMARY KEY (user_id, chapter_id)
);
CREATE TABLE author_earnings (
  id BIGSERIAL PRIMARY KEY, author_id BIGINT NOT NULL REFERENCES users(id),
  story_id BIGINT NOT NULL REFERENCES stories(id), chapter_id BIGINT NOT NULL REFERENCES chapters(id),
  coins INT NOT NULL, share_pct SMALLINT NOT NULL, earned_coins INT NOT NULL,
  payout_id BIGINT, created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE TABLE payout_requests (
  id BIGSERIAL PRIMARY KEY, author_id BIGINT NOT NULL REFERENCES users(id),
  coins INT NOT NULL, amount_minor INT, currency CHAR(3), method VARCHAR(20), details JSONB,
  status VARCHAR(10) NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED','APPROVED','PAID','REJECTED')),
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(), paid_at TIMESTAMPTZ
);
```

## Seed data (V5__seed.sql)
Genres (above), default admin created from env (`ADMIN_EMAIL`, `ADMIN_PASSWORD`) on first startup by a `CommandLineRunner` — never hardcode credentials.

## Notes for the AI
- JPA entities: `@Version` on `chapters`; use `FetchType.LAZY`; use `@EntityGraph`/DTO projections to avoid N+1.
- Counters (`view_count`, `vote_count`…) updated via atomic SQL `UPDATE x SET c = c + 1`, never read-modify-write.
- Use `hibernate.ddl-auto=validate` in all envs. Flyway owns the schema.
