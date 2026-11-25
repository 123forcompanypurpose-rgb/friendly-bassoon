# SRS — Software Requirements Specification (v1.0)

## 1. Introduction
**Purpose:** Define exact, testable requirements for NovelVerse.
**Scope:** Web app (Next.js) + REST API (Spring Boot) + PostgreSQL. Responsive web; no native apps.
**Definitions:** *Story* = a book/series. *Chapter* = a unit of a story. *Writer* = user who publishes. *Reader* = any viewer. *Coin* = virtual currency (Phase 12).

Requirement IDs: `FR-<MODULE>-<NN>` functional, `NFR-<AREA>-<NN>` non-functional. "SHALL" = mandatory.

## 2. Overall description
- **Roles:** `GUEST`, `USER` (reader+writer, same account), `MODERATOR`, `ADMIN`.
- **Operating environment:** modern browsers (last 2 versions Chrome/Safari/Firefox/Edge, Android Chrome, iOS Safari). Backend on Linux container, Java 21.
- **Constraints:** free-tier hosting (limited RAM ~ 512MB–1GB, DB ≤ 0.5GB initially), original-content policy, legal compliance (see SECURITY doc).
- **Dependencies:** PostgreSQL, Redis (optional cache/rate limit), S3-compatible storage (Cloudflare R2), SMTP/email API, Google OAuth, Razorpay & Stripe (Phase 12).

## 3. Functional requirements

### 3.1 Authentication & accounts (AUTH)
- FR-AUTH-01 The system SHALL let a guest register with email, username (3–20 chars, unique, `[a-z0-9_]`), and password (min 8 chars, must not be in a common-password list).
- FR-AUTH-02 The system SHALL send an email verification link (token valid 24h); unverified users SHALL NOT publish stories or comment.
- FR-AUTH-03 The system SHALL support login with email/username + password.
- FR-AUTH-04 The system SHALL support Google OAuth login/registration; accounts with same verified email SHALL be linked.
- FR-AUTH-05 The system SHALL issue a short-lived access token (15 min) and a rotating refresh token (30 days), both in httpOnly, Secure, SameSite cookies. Refresh tokens SHALL be stored hashed and revoked on logout/password change.
- FR-AUTH-06 The system SHALL support forgot/reset password via emailed one-time token (valid 1h).
- FR-AUTH-07 The system SHALL lock login for 15 min after 5 failed attempts per account+IP.
- FR-AUTH-08 Users SHALL be able to edit profile (display name, bio ≤ 500 chars, avatar, social links), change password, and delete their account (soft delete, content anonymized, 30-day grace).
- FR-AUTH-09 Users SHALL set date of birth/18+ confirmation to view mature content.

### 3.2 Stories (STORY)
- FR-STORY-01 A verified user SHALL create a story with: title (3–120), blurb (≤ 2000), cover image (JPG/PNG/WebP ≤ 2MB, auto-resized), primary genre (required), up to 10 tags, language, maturity rating (`EVERYONE`, `TEEN`, `MATURE`), status (`ONGOING`, `COMPLETED`, `HIATUS`).
- FR-STORY-02 Story SHALL have a unique URL slug derived from title (`/novel/{id}-{slug}`); changing title SHALL NOT break old URLs (id is authoritative; redirect to canonical slug).
- FR-STORY-03 Story visibility SHALL be `DRAFT`, `PUBLISHED`, `UNLISTED`, `REMOVED` (by moderator). Only `PUBLISHED` appears in browse/search/sitemap.
- FR-STORY-04 The owner SHALL edit/delete own stories (delete = soft delete; hard delete after 30 days).
- FR-STORY-05 Story detail SHALL show cover, title, author, genre, tags, blurb, status, chapter count, total words, views, votes, rating avg/count, library count, last updated, chapter list (paginated 50), "Start reading"/"Continue" button.

### 3.3 Chapters (CHAP)
- FR-CHAP-01 Owner SHALL create chapters with title (≤ 150), rich-text body (headings, bold, italic, underline, blockquote, lists, hr, links, images) up to 50,000 words, and optional author note (≤ 2000 chars).
- FR-CHAP-02 Body HTML SHALL be sanitized server-side (allow-list) before storing.
- FR-CHAP-03 Editor SHALL autosave a draft every 10 seconds and on blur; draft conflicts resolved by last-write-wins with `version` check and warning.
- FR-CHAP-04 Chapters SHALL have `DRAFT`, `SCHEDULED` (publishAt in future), `PUBLISHED`, `REMOVED` statuses. A scheduler SHALL publish due chapters at most 1 minute late.
- FR-CHAP-05 Chapters SHALL have an integer `number` (order). Owner SHALL reorder chapters (drag/drop) with no gaps/duplicates.
- FR-CHAP-06 The system SHALL compute word count and estimated reading time on save.
- FR-CHAP-07 Reader SHALL see prev/next chapter links and chapter-list drawer.
- FR-CHAP-08 Guests SHALL be able to read published free chapters without login.
- FR-CHAP-09 (Phase 12) Chapters MAY be marked `isPremium` with `coinPrice`; non-unlocked users SHALL see only the first N words (default 150) and an unlock CTA; the full text SHALL NOT be sent to the client until unlocked.

### 3.4 Reading experience (READ)
- FR-READ-01 Reader SHALL adjust font size (5 steps), font family (serif/sans), line spacing, theme (light/sepia/dark), width; preferences SHALL persist (localStorage + account if logged in).
- FR-READ-02 System SHALL save reading progress (story, last chapter, scroll position %) for logged-in users (debounced, ≤ 1 request / 5s) and show "Continue reading".
- FR-READ-03 A chapter view SHALL be counted at most once per user/IP-hash per chapter per 24h (bot UAs excluded). Counting SHALL NOT block rendering (async / batched).
- FR-READ-04 Chapter page SHALL render server-side (SEO) with correct `<title>`, meta description, OpenGraph, JSON-LD (`Book`/`Chapter`).

### 3.5 Library & lists (LIB)
- FR-LIB-01 User SHALL add/remove a story to Library; Library page lists stories with unread chapter count, sorted by recently read/updated.
- FR-LIB-02 User SHALL create named reading lists (public/private) and add stories.
- FR-LIB-03 Adding to library SHALL auto-follow story updates (notification on new chapter) unless disabled.

### 3.6 Discovery (DISC)
- FR-DISC-01 Home SHALL show: featured carousel (admin-curated), trending, new releases, recently updated, genre rows, completed stories, continue reading (if logged in).
- FR-DISC-02 Browse page SHALL filter by genre, tag(s), language, status, min chapters/words, maturity (respecting 18+), and sort by `trending`, `newest`, `updated`, `top_rated`, `most_read`, `most_voted`.
- FR-DISC-03 Search SHALL match title, author name, tags, blurb with typo tolerance (Postgres FTS + trigram), return in < 500ms p95 at 10k stories, and provide autocomplete suggestions.
- FR-DISC-04 Rankings SHALL be computed by scheduled job (daily/weekly/monthly/all-time) from views+votes+library-adds with time decay, cached.
- FR-DISC-05 Every browse/search result page SHALL have shareable URLs (query params).

### 3.7 Social (SOC)
- FR-SOC-01 Users SHALL follow/unfollow other users.
- FR-SOC-02 Users SHALL vote (like) a story and a chapter once each (toggle).
- FR-SOC-03 Users SHALL rate a story 1–5 and optionally write a review (≤ 3000 chars), one per user per story, editable.
- FR-SOC-04 Users SHALL comment on chapters (≤ 2000 chars), reply (one level of nesting), like, edit within 15 min, delete own. Story owner SHALL delete/pin comments on own stories.
- FR-SOC-05 System SHALL rate-limit comments (e.g., 10/min, 100/day).
- FR-SOC-06 Users SHALL block other users (hidden comments, no follow/notifications).

### 3.8 Notifications (NOTIF)
- FR-NOTIF-01 In-app notifications for: new chapter (library/follow), reply to comment, comment on my chapter, new follower, vote milestones, moderation actions, system announcements.
- FR-NOTIF-02 Bell icon SHALL show unread count; list paginated; mark read/all read.
- FR-NOTIF-03 Users SHALL configure email notification preferences (P1: new chapter digest, replies); every email SHALL contain unsubscribe link.

### 3.9 Writer studio (WRITE)
- FR-WRITE-01 Dashboard lists own stories with stats (views, votes, comments, library adds, followers) and last 30 days chart.
- FR-WRITE-02 Per-story page: edit details, manage chapters, view comments, analytics.
- FR-WRITE-03 Preview chapter as reader sees it before publish.

### 3.10 Moderation & admin (ADMIN)
- FR-ADMIN-01 Any logged-in user SHALL report story/chapter/comment/user with reason (`COPYRIGHT`, `SEXUAL_MINORS`, `HATE`, `SPAM`, `HARASSMENT`, `OTHER`) and note.
- FR-ADMIN-02 Moderators SHALL view a report queue, filter by status/type, open target, and take action: dismiss, remove content, warn, suspend (duration), ban. Every action SHALL be audit-logged with actor, target, reason, timestamp.
- FR-ADMIN-03 Removed content SHALL notify the author with reason and appeal instructions.
- FR-ADMIN-04 Admin SHALL manage genres/tags, featured slots, announcements, and users (roles, ban/unban).
- FR-ADMIN-05 DMCA/copyright notice form (public) SHALL create a high-priority report with claimant info; counter-notice flow documented.
- FR-ADMIN-06 Admin dashboard SHALL show: DAU, new users, new stories/chapters, open reports, top stories.

### 3.11 Monetization (PAY) — Phase 12
- FR-PAY-01 Users SHALL buy coin packages via Razorpay (INR) or Stripe (USD/other); provider chosen by user's country/currency preference.
- FR-PAY-02 Coins SHALL be credited ONLY after server-verified payment webhook (signature validated, idempotent by provider event id).
- FR-PAY-03 Wallet SHALL be an append-only ledger (`coin_transactions`); balance = sum; no negative balances; all operations in DB transactions with row locking.
- FR-PAY-04 Unlocking a chapter SHALL debit coins and create an unlock record atomically; unlock is permanent.
- FR-PAY-05 Author earnings SHALL be tracked per unlock (revenue share %), visible in studio; payouts requested manually; admin marks paid.
- FR-PAY-06 Refund/chargeback handling SHALL reverse coins where possible and be logged.

### 3.12 Static/legal pages
- FR-LEGAL-01 Terms, Privacy, Content Policy, DMCA/Copyright, Refund Policy, Contact/Grievance Officer pages SHALL exist and be linked in footer.

## 4. Non-functional requirements

### Performance
- NFR-PERF-01 Chapter and story pages: LCP < 2.5s on simulated 4G; TTFB < 600ms from CDN cache; JS for reader page < 150KB gzip.
- NFR-PERF-02 API p95 < 300ms for reads (excluding cold start), < 800ms for writes at 50 concurrent users.
- NFR-PERF-03 All list endpoints SHALL be paginated (max page size 50); no N+1 queries (verified by tests/logging).
- NFR-PERF-04 Public pages SHALL use ISR/caching (stale-while-revalidate 60s); popular API reads cached (Redis/Caffeine) with TTL.

### Scalability
- NFR-SCAL-01 Backend SHALL be stateless (session in cookies/DB/Redis) to allow horizontal scaling.
- NFR-SCAL-02 Design target v1: 10k users, 5k stories, 200k chapters, 100 concurrent users on free tier.

### Availability & reliability
- NFR-AVAIL-01 Target 99% monthly (free-tier realistic). Health endpoints `/actuator/health`.
- NFR-AVAIL-02 DB backups daily (pg_dump to R2), restore tested once before launch.
- NFR-AVAIL-03 Graceful degradation: if Redis is down, app SHALL still work (skip cache, in-memory rate limit).

### Security (details in SECURITY doc)
- NFR-SEC-01 HTTPS only, HSTS. Passwords hashed with Argon2id or BCrypt(≥12). 
- NFR-SEC-02 OWASP Top 10 mitigations: input validation, output encoding, HTML sanitization, parameterized queries, CSRF protection for cookie auth, CORS allow-list, security headers.
- NFR-SEC-03 Rate limiting on auth, comment, report, search, upload endpoints.
- NFR-SEC-04 Secrets only via env vars; never committed.
- NFR-SEC-05 Uploads validated by magic bytes, size, dimension; stored with random keys; served via CDN.

### Usability & accessibility
- NFR-UX-01 Mobile-first responsive (360px+). Touch targets ≥ 44px.
- NFR-UX-02 WCAG 2.1 AA: contrast, keyboard navigation, alt text, focus states, aria labels.
- NFR-UX-03 Dark mode everywhere.

### SEO
- NFR-SEO-01 SSR for public pages, canonical URLs, `sitemap.xml` (split by 5k URLs), `robots.txt`, OpenGraph/Twitter cards, JSON-LD.

### i18n
- NFR-I18N-01 UI strings externalized (next-intl) with English default; Hindi added in v1.1. Stories carry a `language` code; UTF-8 everywhere; fonts support Devanagari and other Indic scripts.

### Maintainability / quality
- NFR-MAINT-01 Backend unit+integration test coverage ≥ 70% on services; Testcontainers for DB tests.
- NFR-MAINT-02 CI SHALL run build, tests, lint on each PR.
- NFR-MAINT-03 Flyway migrations only; never edit applied migrations.
- NFR-MAINT-04 OpenAPI spec auto-generated and the source of the frontend client.
- NFR-MAINT-05 Structured JSON logs with request id; no PII/passwords in logs.

### Legal/compliance
- NFR-LEG-01 Cookie consent banner for EU users; privacy policy; data export & delete on request.
- NFR-LEG-02 Takedown response target: 24–72h.

## 5. External interfaces
- Email: Brevo/Resend/SMTP (free tier). Storage: S3 API (R2). OAuth: Google. Payments: Razorpay, Stripe webhooks. Optional: Cloudflare Turnstile (captcha), Sentry.

## 6. Data requirements
See `DATABASE_SCHEMA.md`. Retention: logs 30 days; soft-deleted content 30 days; audit logs 1 year; payment records 7 years (accounting law may require—verify locally).

## 7. Acceptance criteria (per module, summary)
- A guest can open a story and read chapter 1 without logging in, page is SSR and has OG tags.
- A new user can register, verify email, create a story with cover, write and publish a chapter, and see it on Home "New releases" within 60s.
- A reader can add to library, close browser, return and tap "Continue reading" to land at saved position.
- A reported comment appears in the moderator queue; removing it hides it everywhere and notifies the author.
- (Phase 12) A paid purchase webhook replayed twice credits coins only once.

## 8. Out of scope v1
Native apps, audio/video, real-time chat, ML recommendations, multi-author collaborative editing, translations marketplace.
