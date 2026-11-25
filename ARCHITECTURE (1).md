# ARCHITECTURE

## 1. Stack
| Layer | Choice | Why |
|---|---|---|
| Backend | **Spring Boot 3.3+, Java 21**, Maven | Your preference; strong for transactions/payments |
| Web framework | Spring Web (MVC), Spring Security, Spring Data JPA (Hibernate), Bean Validation | Conventional, AI-friendly |
| DB | **PostgreSQL 16** (Neon free) | FTS, JSONB, reliable |
| Migrations | **Flyway** | Versioned schema |
| Cache/limits | Caffeine (in-process) + optional Redis (Upstash free); **Bucket4j** for rate limits | Free-tier friendly |
| Auth | Spring Security + JWT (jjwt) in httpOnly cookies; Google OAuth2 | Safe from XSS token theft |
| Storage | Cloudflare R2 (S3 API) via AWS SDK v2, presigned uploads | 10GB free, no egress fees |
| HTML sanitizing | OWASP Java HTML Sanitizer | Prevent XSS in chapters |
| Docs | springdoc-openapi → `/v3/api-docs` | Generate TS client |
| Mapping | MapStruct | DTO mapping |
| Email | Spring Mail via Brevo/Resend SMTP | Free quota |
| Jobs | `@Scheduled` + ShedLock (DB lock) | Publish scheduled chapters, rankings, cleanup |
| Tests | JUnit 5, Mockito, Testcontainers (Postgres), RestAssured/MockMvc | |
| Frontend | **Next.js 15 (App Router), React 19, TypeScript** | SSR/ISR for SEO |
| UI | Tailwind CSS + shadcn/ui + lucide icons | Fast, accessible |
| Data fetching | TanStack Query (client), server `fetch` with revalidate (server components) | |
| Forms | react-hook-form + zod | |
| Editor | **Tiptap** (ProseMirror) outputs HTML | |
| API client | **orval** or **openapi-typescript + openapi-fetch** generated from OpenAPI | No drift |
| i18n | next-intl | |
| State | Minimal: React Query + small Zustand store for reader prefs | |
| E2E | Playwright | |
| CI/CD | GitHub Actions | |
| Monitoring | Sentry free + UptimeRobot + Spring Actuator | |

## 2. System diagram
```
Browser ──► Cloudflare (DNS, CDN, WAF) ─┬─► Next.js (Vercel / Docker)  ── SSR fetch ─┐
                                        │                                           ▼
                                        └─► api.domain.com ─► Spring Boot (Docker) ─► PostgreSQL (Neon)
                                                              │  ├─► Redis (Upstash, optional)
                                                              │  ├─► R2 (images, backups)
                                                              │  └─► SMTP / Razorpay / Stripe / Google
```
Domains: `app domain` = `novelverse.com` (Next.js), `api.novelverse.com` (Spring Boot). Same registrable domain → cookies with `Domain=.novelverse.com`, `SameSite=Lax`, `Secure`, `HttpOnly`.

## 3. Repo layout (monorepo)
```
novelverse/
├─ CLAUDE.md
├─ docs/                      # this pack
├─ backend/
│  ├─ pom.xml
│  ├─ Dockerfile
│  └─ src/main/java/com/novelverse/
│     ├─ NovelverseApplication.java
│     ├─ common/              # ApiError, exceptions, pagination, util, slug, html-sanitizer
│     ├─ config/              # Security, CORS, OpenAPI, Cache, Async, S3
│     ├─ security/            # JwtService, filters, UserPrincipal, rate-limit filter
│     ├─ auth/                # controller, service, dto, entity(RefreshToken, EmailToken)
│     ├─ user/                # User, Profile, Follow, Block
│     ├─ story/               # Story, Genre, Tag, repo, service, controller
│     ├─ chapter/             # Chapter, versions/drafts, scheduler
│     ├─ reading/             # Progress, views counter
│     ├─ library/             # Library, ReadingList
│     ├─ social/              # Comment, Vote, Rating/Review
│     ├─ notification/        # Notification + email
│     ├─ discovery/           # search, browse, rankings
│     ├─ moderation/          # Report, ModerationAction, DMCA, AuditLog
│     ├─ admin/               # admin controllers
│     ├─ storage/             # presign upload, image processing
│     ├─ payment/             # (Phase 12) Wallet, Ledger, PaymentProvider, Razorpay, Stripe
│     └─ jobs/                # scheduled tasks
│     (each feature: web/ (controllers+dto), domain/ (entities, repos), service/)
│  └─ src/main/resources/{application.yml, application-prod.yml, db/migration/V1__init.sql ...}
│  └─ src/test/java/...
├─ frontend/
│  ├─ package.json, next.config.ts, tailwind.config.ts
│  └─ src/
│     ├─ app/
│     │  ├─ (public)/ page.tsx(Home), browse/, search/, novel/[id]/[[...slug]]/, novel/[id]/chapter/[chapterId]/, u/[username]/
│     │  ├─ (auth)/ login, register, forgot-password, reset-password, verify-email
│     │  ├─ (app)/ library, history, notifications, settings
│     │  ├─ studio/ (dashboard, story/new, story/[id], story/[id]/chapter/[cid]/edit, analytics)
│     │  ├─ admin/ (reports, users, stories, genres, featured, announcements)
│     │  ├─ legal/ terms, privacy, content-policy, dmca, refund
│     │  ├─ sitemap.ts, robots.ts, layout.tsx, not-found.tsx, error.tsx
│     ├─ components/ ui/ (shadcn), layout/, story/, reader/, editor/, comments/, common/
│     ├─ lib/ api/ (generated client), auth/, utils/, seo/
│     ├─ hooks/, stores/, i18n/, styles/
│     └─ middleware.ts        # route protection (studio/admin/app)
└─ .github/workflows/{backend-ci.yml, frontend-ci.yml, deploy.yml}
```

## 4. Key design decisions

### 4.1 Layering (backend)
`Controller (DTO in/out, validation)` → `Service (business logic, @Transactional)` → `Repository (Spring Data)` → `Entity`. Controllers never touch repositories. Entities never returned from controllers. Use MapStruct DTOs. Package-by-feature; cross-feature calls only through services.

### 4.2 Auth flow
1. Login → server sets `access_token` (15m JWT, claims: sub=userId, roles) and `refresh_token` (opaque random 256-bit, SHA-256 hash stored in `refresh_tokens`, 30d) cookies.
2. Next.js server components forward cookies to API (`credentials: 'include'` client-side; manual cookie forwarding server-side).
3. On 401 the client calls `POST /auth/refresh` once, rotates refresh token (reuse detection → revoke family), retries.
4. CSRF: cookie auth + `SameSite=Lax` + require custom header `X-Requested-With: novelverse` (or double-submit token) on state-changing requests; CORS allow-list only the web origin.

### 4.3 Chapter content
Stored as **sanitized HTML** in `chapters.content_html` (TEXT) plus `content_text` is NOT duplicated; word count stored. Drafts in `chapter_drafts` (one per chapter) so published content stays stable. Large images in chapters go to R2 (URL only). Premium: API returns `contentHtml` truncated + `locked=true` unless unlocked.

### 4.4 Search
Postgres: `tsvector` generated column on stories (title weight A, tags B, blurb C) + `pg_trgm` index for fuzzy title/author. Endpoint `/search`. Revisit Meilisearch/Typesense when >100k stories.

### 4.5 View counting
Redis `INCR`/in-memory map keyed by `chapterId:ipHash:day` for dedupe; flush aggregated counts to DB every 60s via scheduled job (`UPDATE ... SET views = views + :n`). If Redis absent, use Caffeine with TTL.

### 4.6 Rankings
Nightly/hourly job computes `score = (views*1 + votes*5 + library_adds*8 + comments*3) / (age_hours+2)^1.3` into `story_rank_snapshots`; endpoints read snapshots (cached 5 min).

### 4.7 Scheduling & multi-instance safety
ShedLock with JDBC provider so jobs don't run twice if scaled.

### 4.8 Errors
RFC 7807-like JSON: `{ "timestamp","status","code","message","path","traceId","fieldErrors":[{field,message}] }`. Global `@RestControllerAdvice`. Stable `code` strings (e.g., `AUTH_INVALID_CREDENTIALS`, `STORY_NOT_FOUND`) for frontend i18n.

### 4.9 Pagination
Offset (`page`,`size`) for admin/lists; **keyset/cursor** for feeds/comments/notifications. Response: `{content:[], page, size, totalElements, totalPages}` or `{items:[], nextCursor}`.

### 4.10 Caching rules
- Next.js: `revalidate = 60` for Home/Browse, `300` for story/chapter pages; on-demand revalidate via secured webhook when owner publishes (`POST /api/revalidate` with secret).
- Spring: `@Cacheable` for genres/tags, home sections (TTL 60s), rank lists (5m).

### 4.11 Payments (Phase 12)
`PaymentProvider` interface (`createOrder`, `verifyWebhook`, `refund`) with `RazorpayProvider`, `StripeProvider`. Ledger-based wallet; idempotency table `payment_events(provider,event_id UNIQUE)`. Never trust client success callbacks—only webhooks.

### 4.12 Config & profiles
`dev` (Docker Compose Postgres), `test` (Testcontainers), `prod`. All secrets via env vars. `.env.example` committed, `.env` ignored.

### 4.13 Local dev
`docker-compose.yml` at root: postgres:16, redis:7, mailpit (fake SMTP), minio (S3). `make dev` or README commands to run backend (`./mvnw spring-boot:run`) and frontend (`pnpm dev`).

## 5. Free-tier JVM tuning (important)
Run with: `-XX:+UseSerialGC -Xms128m -Xmx384m -XX:MaxMetaspaceSize=128m -Xss512k -XX:TieredStopAtLevel=1` (lower memory, faster startup). Use `spring.main.lazy-initialization=true` only if startup is too slow. Hikari pool max 5 for Neon free tier. Consider Spring Boot layered jars and (later) GraalVM native image.
