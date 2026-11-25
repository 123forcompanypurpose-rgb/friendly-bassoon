# TASKS (AI: tick boxes as you finish; keep this file updated)

## Phase 0 — Setup
- [ ] Monorepo structure, backend skeleton, frontend skeleton
- [ ] docker-compose (postgres, redis, mailpit, minio)
- [ ] Env examples, README, .gitignore
- [ ] CI workflows (backend, frontend)
- [ ] Health endpoint + frontend health display

## Phase 1 — DB + Auth
- [ ] Flyway V1–V5 migrations applied cleanly
- [ ] Register / verify email / resend
- [ ] Login / logout / logout-all / refresh rotation + reuse detection
- [ ] Forgot / reset / change password
- [ ] Google login
- [ ] Lockout + rate limiting
- [ ] Security config (CORS, CSRF header, headers)
- [ ] Global error handler + error codes
- [ ] User profile, follow, block, avatar presign, delete account
- [ ] Admin bootstrap from env
- [ ] Auth integration tests

## Phase 2 — Stories & Chapters API
- [ ] Genres/tags endpoints + seed
- [ ] Story CRUD + slug + publish rules
- [ ] Cover upload (presign + resize)
- [ ] Chapter CRUD + draft autosave + version conflict
- [ ] Publish / schedule / unpublish + scheduler (ShedLock)
- [ ] Reorder chapters
- [ ] HTML sanitizer + tests
- [ ] Ownership/IDOR tests

## Phase 3 — Frontend foundation & reading
- [ ] App shell, theme, i18n, mobile tab bar
- [ ] Auth pages + session handling + middleware
- [ ] Home, Browse, Story detail, Reader (+settings)
- [ ] View beacon, SEO (metadata, JSON-LD, sitemap, robots)
- [ ] Loading/empty/error states

## Phase 4 — Writer Studio
- [ ] Dashboard, story form, cover cropper
- [ ] Chapter list reorder
- [ ] Tiptap editor + autosave + publish/schedule
- [ ] On-demand revalidation
- [ ] Playwright E2E (write → publish → read)

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
