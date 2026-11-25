# AI BUILD PLAYBOOK — end-to-end, phase by phase

**How to use:** open your AI tool in the repo root. For each phase paste the prompt (it tells the AI to read the docs). Review → run → test → commit → tick `TASKS.md` → next phase.
**Golden rules:** one phase at a time · always run & test · commit after each phase · if AI drifts, say "Re-read docs/CLAUDE.md and the relevant doc, then fix."
**Time estimate (part-time):** ~10–14 weeks to public v1.

Recommended first message in any new AI session:
> Read `CLAUDE.md` and all files in `/docs`. Summarize the project, stack, and rules in 10 lines. Then wait for my phase prompt.

---

## Phase 0 — Project setup & local environment
```
Read CLAUDE.md and docs/ARCHITECTURE.md. Set up the monorepo exactly as in ARCHITECTURE.md section 3:
1. backend/: Spring Boot 3.3 + Java 21 + Maven project (deps: web, validation, security, data-jpa, postgresql, flyway-core + flyway-database-postgresql, actuator, mail, springdoc-openapi, jjwt, lombok, mapstruct, bucket4j, caffeine, shedlock, AWS SDK v2 S3, owasp-java-html-sanitizer, testcontainers, spotless).
2. frontend/: Next.js 15 App Router + TypeScript strict + Tailwind + shadcn/ui + ESLint + Prettier + TanStack Query + react-hook-form + zod + next-intl + vitest + playwright.
3. Root docker-compose.yml: postgres:16, redis:7, mailpit, minio (create bucket 'novelverse').
4. application.yml with dev/test/prod profiles and env placeholders; .env.example for both apps; .gitignore; README with run commands.
5. GitHub Actions: backend-ci.yml (build+test) and frontend-ci.yml (lint+test+build).
6. A /api/v1/health endpoint on backend and a home page on frontend that calls it.
Acceptance: `docker compose up -d`, backend starts on :8080, frontend on :3000 and shows backend health status; CI files valid. Update TASKS.md.
```

## Phase 1 — Database + Auth (backend)
```
Read docs/DATABASE_SCHEMA.md (V1,V2,V3), docs/SRS.md §3.1, docs/API_SPEC.md (Auth, Users), docs/SECURITY_AND_COMPLIANCE.md §1.
Implement:
1. Flyway migrations V1..V5 (core, social, moderation, search trigger, seed genres). Hibernate ddl-auto=validate.
2. Auth module: register, verify-email, login, refresh (rotation + reuse detection), logout, logout-all, forgot/reset/change password, Google login (verify Google ID token), account lockout, httpOnly cookie JWT.
3. SecurityConfig: stateless, CORS allow-list from env, CSRF header check for state-changing requests, method security, security headers.
4. Global exception handler with the standard error JSON + error codes.
5. Rate limiting (Bucket4j) on auth endpoints. Email sending via JavaMailSender (Mailpit in dev) with simple HTML templates.
6. User module: GET/PATCH /me, public profile, follow/unfollow, block, avatar presign (S3/MinIO), delete account.
7. Bootstrap admin from env ADMIN_EMAIL/ADMIN_PASSWORD if no admin exists.
8. Tests: Testcontainers integration tests for every auth flow including failure cases (wrong password, locked, expired token, refresh reuse).
Acceptance: all tests pass; Swagger UI works at /swagger-ui; OpenAPI at /v3/api-docs. Update TASKS.md.
```

## Phase 2 — Stories, genres, tags, chapters, studio API
```
Read SRS §3.2, §3.3, API_SPEC (Catalog, Stories, Chapters), DATABASE_SCHEMA.
Implement backend modules: story, chapter, storage.
- Story CRUD, slug generation, publish/unpublish rules, tag handling (create-or-reuse, max 10), cover presign upload with validation and image resize to WebP (use Thumbnailator or imgscalr) ≤ 800px wide.
- Chapter create, draft autosave with optimistic version check (409), publish/unpublish, schedule, reorder (transactional with deferred unique), soft delete, word count, HTML sanitization (OWASP) with allow-list from SECURITY doc.
- Denormalized counters on story (chapter_count, word_count, last_chapter_at) kept consistent in service transactions.
- GET chapter endpoint returns prev/next ids; guests allowed; DRAFT/REMOVED only for owner/mod; MATURE gating per user flags.
- ShedLock + @Scheduled job that publishes due SCHEDULED chapters every minute.
- Ownership/IDOR tests: user B cannot read/edit user A's draft or story.
Acceptance: Postman/Swagger flow: register → verify → create story → create chapter → publish → public GET works. Tests pass. Update TASKS.md.
```

## Phase 3 — Frontend foundation + auth UI + public reading
```
Read UI_UX_SPEC.md and CLAUDE.md frontend rules. First run `pnpm gen:api` to generate the typed client from the backend OpenAPI (add the script if missing).
Build:
1. App shell: header (logo, search, genre menu, Write button, notification bell placeholder, user menu), mobile bottom tab bar, footer with legal links, theme toggle (light/dark/sepia), next-intl set up (en).
2. Auth pages: login, register (with Turnstile placeholder), verify-email, forgot/reset password, Google sign-in button; cookie-based session helper for server and client; middleware.ts protecting /studio, /library, /settings, /admin.
3. Public pages with SSR+ISR: Home (/ using GET /home — implement a simple backend /home endpoint now if missing), Browse (/browse with filters synced to URL), Story detail, Chapter reader with settings sheet (font, size, spacing, theme, width persisted), prev/next, TOC drawer, view beacon.
4. SEO: generateMetadata, OG tags, JSON-LD Book, sitemap.ts, robots.ts.
5. Skeletons, empty and error states everywhere; responsive 360px+.
Acceptance: a guest can browse and read published sample chapter; Lighthouse mobile performance ≥ 85 on reader; no TypeScript errors. Update TASKS.md.
```

## Phase 4 — Writer Studio (frontend)
```
Read UI_UX_SPEC §3 (studio) and §5 (editor), SRS §3.9.
Build /studio dashboard, story create/edit form with cover upload + cropper (2:3, react-easy-crop) via presigned upload, tag input with autocomplete, chapter list with drag-and-drop reorder (dnd-kit), and the Tiptap editor with autosave (every 10s + blur), save status indicator, word count, preview, publish/schedule/unpublish, unsaved-change warning, version-conflict (409) dialog, inline image upload. Image upload goes to presigned URL then inserts the CDN URL.
Add on-demand revalidation: after publish/edit, backend calls frontend POST /api/revalidate (secret) with paths to refresh.
Add Playwright E2E: register → verify (read email from Mailpit API) → create story → write chapter → publish → see it as guest.
Acceptance: E2E passes. Update TASKS.md.
```

## Phase 5 — Library, reading progress, lists, history
```
Read SRS §3.4–3.5, API_SPEC (Reading, library, lists).
Backend: reading_progress upsert, continue-reading, history, library add/remove with notify flag, unread counts (chapters published after last read chapter), reading lists CRUD.
Frontend: Add-to-library button (optimistic), /library page with tabs (Library, History, Lists), "Continue reading" row on Home, auto-save progress (debounced + sendBeacon), resume at scroll position.
Tests for unread count and progress upsert. Update TASKS.md.
```

## Phase 6 — Social: votes, ratings/reviews, comments, follows
```
Read SRS §3.7, API_SPEC (Comments, votes, reviews).
Backend: story/chapter votes (toggle, atomic counters), reviews (one per user, recompute avg via rating_sum/count), comments (1-level replies, like, edit ≤15min, delete, pin by story owner, rate limits, block-list filtering), follow endpoints already exist.
Frontend: VoteButton, RatingStars + review form + reviews tab, CommentThread (cursor pagination, optimistic add, replies, like, report), follow button on profile, user profile page /u/[username].
Sanitization: comments/reviews plain text only. Tests incl. spam rate limit and permission matrix. Update TASKS.md.
```

## Phase 7 — Discovery: search, browse, rankings, home feeds
```
Read SRS §3.6, ARCHITECTURE §4.4 and §4.6.
Implement Postgres FTS (tsvector + pg_trgm) search with ranking and typo tolerance, /search/suggest, advanced browse filters/sorts with proper indexes (EXPLAIN-check the main queries), ranking snapshot scheduled job, /rankings, /home aggregation with Caffeine cache (60s), genre landing pages.
Frontend: search bar with debounce autocomplete, /search, /rankings, /genre/[slug], infinite scroll on browse.
Create a seed script (profile 'dev' only) that generates 30 stories / 300 chapters of ORIGINAL placeholder text (lorem or public-domain) with covers (generated gradients) to test performance.
Acceptance: search p95 < 500ms on seed data. Update TASKS.md.
```

## Phase 8 — Notifications & email
```
Read SRS §3.8, DATABASE notifications tables.
Backend: notification creation via domain events (@TransactionalEventListener AFTER_COMMIT): new chapter → library users with notify=true + followers (batch insert, async), comment reply, chapter comment to author, new follower, moderation messages. Unread count endpoint, list (cursor), mark read(all). Email: transactional emails (verify/reset) and optional reply emails honoring prefs; unsubscribe token endpoint.
Frontend: bell with unread count (poll 60s, pause when tab hidden), /notifications page, settings for notification prefs.
Tests: fan-out for a story with 1000 library users stays fast (batched). Update TASKS.md.
```

## Phase 9 — Moderation, admin panel, legal pages
```
Read SRS §3.10, SECURITY_AND_COMPLIANCE §2–5, API_SPEC admin.
Backend: reports, dmca endpoint, moderation actions (remove/restore content, warn, suspend, ban/unban), audit log, featured slots, announcements, genre/tag admin, admin stats, role change (ADMIN only). Banned/suspended users blocked at auth filter. Removed content hidden everywhere (queries must filter).
Frontend: ReportDialog on story/chapter/comment/profile; /admin with report queue (filters, detail view with content snapshot, action modal with reason), users, stories, genres/tags, featured, announcements, stats dashboard. Age gate interstitial for MATURE, cookie banner, legal pages (Terms, Privacy, Content Policy, DMCA, Refund, Cookie, About, Contact) using clear TEMPLATE text marked "REVIEW WITH A LAWYER".
Tests: permission matrix; removed content cannot be fetched by public. Update TASKS.md.
```

## Phase 10 — Hardening, performance, quality
```
Read SECURITY_AND_COMPLIANCE §1 & §7 and SRS NFRs.
1. Security pass: walk the checklist; add missing rate limits, headers (CSP for Next.js with nonce), IDOR tests, XSS tests with malicious payloads in chapter/comment/bio, dependency audit.
2. Performance: enable HTTP caching headers for public GETs (ETag/Cache-Control), review slow queries with pg_stat/EXPLAIN, add missing indexes, ensure no N+1 (add Hibernate statistics test), tune Hikari (max 5), JVM flags per ARCHITECTURE §5, next/image & font optimization, bundle analysis for reader.
3. Observability: JSON logs w/ traceId, Actuator health/metrics (secured), Sentry on both apps (DSN via env).
4. Accessibility audit with axe (Playwright) on key pages; fix issues.
5. i18n: extract all strings; add Hindi (hi) translation file with machine draft marked TODO-review.
6. Data export + delete account flows complete.
7. Raise coverage to ≥70% services; add Playwright smoke tests for top 10 journeys.
Acceptance: CI green; checklist in SECURITY doc ticked or justified. Update TASKS.md.
```

## Phase 11 — Production build & deployment (free tier)
```
Read docs/DEPLOYMENT.md fully.
1. backend/Dockerfile (multi-stage, layered jar, non-root user, JVM flags for 512MB) and .dockerignore; frontend Dockerfile (standalone output) for self-hosting option.
2. docker-compose.prod.yml (backend + frontend + caddy reverse proxy with automatic HTTPS) for the Oracle VM option.
3. GitHub Actions deploy workflow: on push to main → run tests → build & push images to GHCR → SSH deploy (pull + restart) ; frontend deploys via Vercel/Cloudflare integration.
4. Production application-prod.yml with env-driven config; Flyway runs on start; health checks; graceful shutdown.
5. Scripts: scripts/backup.sh (pg_dump → R2 via rclone/aws-cli), scripts/restore.md, cron instructions.
6. Write DEPLOY_RUNBOOK.md with exact commands I must run manually (create accounts, set env vars, DNS records, first admin login) — assume I am a beginner.
Acceptance: I can follow the runbook and get a live HTTPS site with working signup, email, image upload. Update TASKS.md.
```

## Phase 12 — Monetization: coins & premium chapters (after launch traction)
```
Read SRS §3.3 (FR-CHAP-09), §3.11, DATABASE V12, ARCHITECTURE §4.11, SECURITY §6.
Implement: PaymentProvider interface + RazorpayProvider + StripeProvider (test mode), coin packages, checkout, webhooks with signature verification and idempotency (payment_events), append-only ledger + wallet with row locking, chapter unlock (atomic debit + unlock record + author earnings), server-side gating (premium text never sent unless unlocked), author earnings page, payout requests, admin payout approval, daily check-in.
Frontend: /coins, wallet, unlock modal in reader, studio: mark chapters premium with price (rules: first N chapters must remain free, min/max price), earnings dashboard.
Tests: webhook replay credits once; concurrent unlocks cannot overspend; refund reverses coins.
Acceptance: end-to-end test-mode purchase → unlock works in both INR (Razorpay) and USD (Stripe). Update TASKS.md.
```

## Phase 13 — Growth & polish (optional, ongoing)
PWA/installable, Hindi + more Indic languages, recommendations ("readers also liked"), email digests, writer contests/events, referral coins, ads slots, paragraph comments, audio (TTS), Meilisearch, CDN image optimization, native app wrapper.

---

## Debugging prompts (reuse)
- "Run the build/tests, read the full error, find the root cause, fix it, re-run until green. Explain the cause in 2 lines."
- "Review the code you just wrote against docs/SECURITY_AND_COMPLIANCE.md §1 and docs/API_SPEC.md; list gaps then fix them."
- "Write the missing tests for <module> following CLAUDE.md. Include 401/403/404/validation cases."
- "Refactor <file> – it's too long. Keep behavior identical; tests must stay green."
- "Profile this endpoint: show SQL executed (enable hibernate stats), find N+1, fix with fetch join/projection."

## Tips for working with AI on this project
- Keep sessions focused: new chat per phase, start with the "read CLAUDE.md" message.
- Paste exact error messages, not screenshots of them.
- Ask the AI to "explain what you did and how to run it" after each phase — you have some Java experience, so read the diffs.
- Commit often; if AI breaks things, `git reset`/revert is your safety net.
