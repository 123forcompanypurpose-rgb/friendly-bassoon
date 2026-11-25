# TASKS (AI: tick boxes as you finish; keep this file updated)

## Phase 0 — Setup
- [x] Monorepo structure, backend skeleton, frontend skeleton
- [x] docker-compose (postgres, redis, mailpit, minio)
- [x] Env examples, README, .gitignore
- [x] CI workflows (backend, frontend)
- [x] Health endpoint + frontend health display

## Phase 1 — DB + Auth
- [ ] Flyway V1–V5 migrations applied cleanly (Testcontainers could not start locally; see `DECISIONS.md`)
- [x] Register / verify email / resend
- [x] Login / logout / logout-all / refresh rotation + reuse detection
- [x] Forgot / reset / change password
- [x] Google login
- [x] Lockout + rate limiting
- [x] Security config (CORS, CSRF header, headers)
- [x] Global error handler + error codes
- [x] User profile, follow, block, avatar presign, delete account
- [x] Admin bootstrap from env
- [x] Auth integration tests (authored; local execution requires a compatible Docker API)

## Phase 2 — Stories & Chapters API
- [x] Genres/tags endpoints + seed
- [x] Story CRUD + slug + publish rules
- [x] Cover upload (presign + resize)
- [x] Chapter CRUD + draft autosave + version conflict
- [x] Publish / schedule / unpublish + scheduler (ShedLock)
- [x] Reorder chapters
- [x] HTML sanitizer + tests
- [x] Ownership/IDOR tests (integration test authored; local execution requires a compatible Docker API)

## Phase 3 — Frontend foundation & reading
- [x] App shell, theme, i18n, mobile tab bar
- [x] Auth pages + session handling + middleware
- [x] Home, Browse, Story detail, Reader (+settings)
- [x] View beacon, SEO (metadata, JSON-LD, sitemap, robots)
- [x] Loading/empty/error states

> Phase 3 checks: frontend lint, tests, and production build pass; backend
> verification passes. PostgreSQL/Testcontainers integration tests remain
> skipped because the available Docker API is older than the minimum supported
> version; see `DECISIONS.md`.
> Browser API requests use the same-origin Next.js proxy, avoiding remote
> Codespace browsers interpreting `localhost:8080` as the viewer's device.

## Phase 4 — Writer Studio
- [x] Dashboard, story form, cover cropper
- [x] Chapter list reorder
- [x] Tiptap editor + autosave + publish/schedule
- [x] On-demand revalidation
- [x] Playwright E2E (write → publish → read)

## Phase 5 — Library & progress
- [ ] Progress API + resume
- [ ] Library + unread counts
- [ ] Reading lists
- [ ] Library/History UI + Continue reading row

## Phase 6 — Social
- [ ] Votes (story/chapter)
- [ ] Reviews & ratings
- [ ] Comments (replies, likes, pin, delete, edit)
- [ ] Profile page + follow UI

## Phase 7 — Discovery
- [ ] FTS + trigram search + suggest
- [ ] Browse filters/sorts + indexes verified
- [ ] Rankings job + page
- [ ] Home aggregation + cache
- [ ] Dev seed data (original/public-domain only)

## Phase 8 — Notifications
- [ ] Event-driven notifications + fan-out batching
- [ ] Bell, list, mark read
- [ ] Email prefs + unsubscribe

## Phase 9 — Moderation & Legal
- [ ] Reports + DMCA form
- [ ] Admin queue + actions + audit log
- [ ] Suspend/ban enforcement
- [ ] Featured, announcements, genre/tag admin, stats
- [ ] Age gate, cookie banner
- [ ] Legal pages (reviewed by a lawyer)

## Phase 10 — Hardening
- [ ] Security checklist pass + XSS/IDOR tests
- [ ] Caching headers, indexes, N+1 check
- [ ] Sentry, JSON logs, health/metrics
- [ ] Accessibility audit
- [ ] Hindi i18n
- [ ] Data export + delete
- [ ] Coverage ≥ 70% + E2E smoke tests

## Phase 11 — Deployment
- [ ] Dockerfiles + compose.prod + Caddy
- [ ] CI/CD deploy workflow
- [ ] Backups + restore drill
- [ ] Domain, DNS, HTTPS, cookies verified cross-subdomain
- [ ] Go-live checklist (DEPLOYMENT.md) complete

## Phase 12 — Monetization
- [ ] Payment providers (Razorpay, Stripe) test mode
- [ ] Wallet ledger + webhooks idempotent
- [ ] Premium chapters + unlock
- [ ] Author earnings + payouts
- [ ] Legal/tax review before live mode
