# DEPLOYMENT GUIDE (free-tier first)

> Free-tier limits and terms change often. **Verify each provider's current limits and terms before relying on them.** Notably: Vercel's free (Hobby) plan is for non-commercial use, so once you earn money (coins/ads) move the frontend to a paid plan, Cloudflare Pages, or self-host it (Option C).

## 1. Recommended free topology
| Component | Service | Notes |
|---|---|---|
| Domain | Buy one (~₹700–1000/yr) — the only unavoidable cost | Namecheap/GoDaddy/Cloudflare Registrar |
| DNS/CDN/WAF | **Cloudflare** (free) | proxy, caching, bot protection, Turnstile captcha |
| Frontend | **Vercel Hobby** (launch/testing) → later Cloudflare Pages / self-host | `novelverse.com` |
| Backend | **Option A: Oracle Cloud Always Free VM** (best resources) · **Option B: Render free web service** (easiest, sleeps when idle) | `api.novelverse.com` |
| Database | **Neon** free Postgres (or Supabase free) | use pooled connection string, `sslmode=require` |
| Object storage | **Cloudflare R2** free tier | covers + chapter images + DB backups |
| Redis (optional) | **Upstash** free | app must work without it |
| Email | **Brevo** (≈300/day free) or Resend free | verify domain with SPF/DKIM/DMARC |
| Errors | Sentry free | |
| Uptime | UptimeRobot free (5-min pings) | also keeps Render awake |
| CI/CD | GitHub Actions + GHCR | |

Same registrable domain for web and api is required for cookies (`Domain=.novelverse.com`).

## 2. Accounts & prerequisites checklist
- [ ] GitHub repo (private) · [ ] Domain · [ ] Cloudflare · [ ] Neon · [ ] R2 bucket + API token · [ ] Brevo/Resend · [ ] Google Cloud OAuth client (web) · [ ] Sentry · [ ] Vercel · [ ] Oracle Cloud (or Render) · [ ] Turnstile site/secret keys · [ ] (later) Razorpay + Stripe

## 3. Environment variables
### Backend (`backend/.env` locally; secrets in host/GitHub for prod)
| Var | Example / note |
|---|---|
| `SPRING_PROFILES_ACTIVE` | `prod` |
| `DB_URL` | `jdbc:postgresql://<neon-host>/neondb?sslmode=require` |
| `DB_USER`, `DB_PASSWORD` | from Neon |
| `JWT_SECRET` | 64+ random chars (`openssl rand -base64 64`) |
| `APP_WEB_ORIGIN` | `https://novelverse.com` (CORS) |
| `APP_COOKIE_DOMAIN` | `.novelverse.com` |
| `S3_ENDPOINT` | `https://<accountid>.r2.cloudflarestorage.com` |
| `S3_BUCKET`, `S3_ACCESS_KEY`, `S3_SECRET_KEY`, `S3_PUBLIC_BASE_URL` | R2 + public domain `https://cdn.novelverse.com` |
| `MAIL_HOST/PORT/USER/PASSWORD`, `MAIL_FROM` | Brevo SMTP |
| `GOOGLE_CLIENT_ID` | OAuth |
| `TURNSTILE_SECRET` | |
| `REDIS_URL` | optional |
| `ADMIN_EMAIL`, `ADMIN_PASSWORD` | first admin (change password immediately; remove var afterwards) |
| `REVALIDATE_URL`, `REVALIDATE_SECRET` | frontend revalidation webhook |
| `SENTRY_DSN` | |
| `JAVA_TOOL_OPTIONS` | `-XX:+UseSerialGC -Xms128m -Xmx384m -XX:MaxMetaspaceSize=128m -Xss512k` |
### Frontend
`NEXT_PUBLIC_SITE_URL`, `NEXT_PUBLIC_API_URL` (`https://api.novelverse.com/api/v1`), `API_INTERNAL_URL` (server-side), `NEXT_PUBLIC_GOOGLE_CLIENT_ID`, `NEXT_PUBLIC_TURNSTILE_SITE_KEY`, `REVALIDATE_SECRET`, `NEXT_PUBLIC_SENTRY_DSN`.

## 4. Step-by-step

### Step 1 — Database (Neon)
1. Create project → region closest to users/VM (Singapore/Mumbai for India).
2. Copy connection details; use the **pooled** host for the app. Create a role for the app (not owner) if possible.
3. Flyway runs migrations on first backend start.

### Step 2 — Storage (R2)
1. Create bucket `novelverse-media`; add R2 API token (Object Read & Write).
2. Connect custom domain `cdn.novelverse.com` to the bucket (public). Add CORS rule allowing `PUT` from `https://novelverse.com` for presigned uploads.
3. Create second private bucket `novelverse-backups`.

### Step 3A — Backend on Oracle Cloud Always Free VM (recommended)
1. Create Always Free **Ampere A1 (ARM)** instance (e.g., 2 OCPU / 12 GB) Ubuntu 22.04/24.04; open ports 80/443 in the VCN security list **and** in `iptables/ufw`.
2. Install Docker: `curl -fsSL https://get.docker.com | sh && sudo usermod -aG docker $USER`.
3. Build images for **linux/arm64** (GitHub Actions `docker buildx --platform linux/arm64,linux/amd64`).
4. On the VM create `/opt/novelverse/` with `docker-compose.prod.yml`, `.env`, `Caddyfile`:
```
# Caddyfile
api.novelverse.com {
  encode gzip
  reverse_proxy backend:8080
}
```
```yaml
# docker-compose.prod.yml (backend + caddy; add frontend service for Option C)
services:
  backend:
    image: ghcr.io/<you>/novelverse-backend:latest
    restart: unless-stopped
    env_file: .env
    healthcheck: { test: ["CMD","wget","-qO-","http://localhost:8080/actuator/health"], interval: 30s, retries: 5, start_period: 90s }
    mem_limit: 700m
  caddy:
    image: caddy:2
    restart: unless-stopped
    ports: ["80:80","443:443"]
    volumes: ["./Caddyfile:/etc/caddy/Caddyfile","caddy_data:/data"]
    depends_on: [backend]
volumes: { caddy_data: {} }
```
5. Cloudflare DNS: `A api → VM public IP` (proxied, SSL mode **Full (strict)**).
6. `docker compose -f docker-compose.prod.yml up -d` then `curl https://api.novelverse.com/actuator/health`.
Note: Oracle may reclaim idle free instances; keep light activity (monitoring pings) and keep backups.

### Step 3B — Backend on Render free (simpler, slower)
1. New Web Service from GitHub repo → Docker → `backend/Dockerfile`. Add env vars. Health check path `/actuator/health`.
2. Free instances sleep after ~15 min idle (30–60s cold start). Mitigate with UptimeRobot ping every 5 min (check provider's terms) and cache aggressively on the frontend (ISR) so public pages don't hit the API on every request.
3. Add custom domain `api.novelverse.com` (CNAME) in Render + Cloudflare.

### Step 4 — Frontend
**Vercel (launch):** Import repo → root dir `frontend` → set env vars → deploy → add domain `novelverse.com` and `www` redirect. Set `NEXT_PUBLIC_API_URL`. 
**Option C — self-host on the same Oracle VM (commercial-safe, free):** add to compose:
```yaml
  frontend:
    image: ghcr.io/<you>/novelverse-frontend:latest   # Next.js 'output: standalone'
    restart: unless-stopped
    env_file: .env.frontend
    mem_limit: 400m
```
and Caddy block `novelverse.com { reverse_proxy frontend:3000 }`.

### Step 5 — Email
Add domain in Brevo/Resend → add SPF, DKIM, DMARC DNS records → set SMTP vars → send a test verification email → check spam placement.

### Step 6 — Google OAuth
Google Cloud Console → OAuth consent screen (External) → create Web client; Authorized JS origins `https://novelverse.com`; use Google Identity Services on the frontend and POST the ID token to `/auth/google`.

### Step 7 — Cloudflare settings
SSL Full (strict) · Always HTTPS · Brotli · cache rule: cache `/_next/static/*` and `cdn.` aggressively · WAF managed rules on · Bot Fight Mode on · rate-limiting rule on `/api/v1/auth/*` (free plan allows basic rule).

### Step 8 — CI/CD (GitHub Actions)
`deploy.yml` (on push to `main`): test → buildx build/push to GHCR → SSH to VM (`appleboy/ssh-action`) → `docker compose pull && docker compose up -d && docker image prune -f`. Secrets: `SSH_HOST`, `SSH_USER`, `SSH_KEY`, `GHCR_TOKEN`. Protect `main` (PR + passing CI).
Rollback: re-deploy previous image tag (tag images with git SHA, keep `latest` too).

### Step 9 — Backups
`scripts/backup.sh` nightly via cron on the VM:
```bash
#!/usr/bin/env bash
set -euo pipefail
TS=$(date +%F-%H%M)
pg_dump "$DB_DUMP_URL" | gzip > /tmp/novelverse-$TS.sql.gz
rclone copyto /tmp/novelverse-$TS.sql.gz r2:novelverse-backups/db/novelverse-$TS.sql.gz
rclone delete r2:novelverse-backups/db --min-age 14d
rm /tmp/novelverse-$TS.sql.gz
```
`crontab -e` → `30 2 * * * /opt/novelverse/scripts/backup.sh >> /var/log/nv-backup.log 2>&1`. **Do a restore drill into a scratch Neon branch before launch.** Neon also provides point-in-time restore on some plans — check.

### Step 10 — Monitoring
UptimeRobot monitors: `https://novelverse.com`, `https://api.novelverse.com/actuator/health`. Sentry alerts to email. Check Neon storage (0.5 GB limit) and R2 usage monthly. Optional: Grafana Cloud free + Prometheus metrics from Actuator.

## 5. Dockerfile reference (backend)
```dockerfile
FROM eclipse-temurin:21-jdk AS build
WORKDIR /app
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN ./mvnw -q dependency:go-offline
COPY src src
RUN ./mvnw -q -DskipTests package && java -Djarmode=layertools -jar target/*.jar extract

FROM eclipse-temurin:21-jre
RUN useradd -r -u 1001 app && apt-get update && apt-get install -y --no-install-recommends wget && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/dependencies/ ./
COPY --from=build /app/spring-boot-loader/ ./
COPY --from=build /app/snapshot-dependencies/ ./
COPY --from=build /app/application/ ./
USER app
EXPOSE 8080
ENTRYPOINT ["java","org.springframework.boot.loader.launch.JarLauncher"]
```
Frontend: standard Next.js standalone Dockerfile (`output: 'standalone'`, copy `.next/standalone`, `.next/static`, `public`).

## 6. Pre-launch (go-live) checklist
**Functionality:** signup/verify email/login/Google · create story w/ cover · write/publish/schedule chapter · read as guest · library/progress · comments · search · notifications · report → admin action.
**Security:** HTTPS everywhere · cookies Secure/HttpOnly · CORS allow-list verified · rate limits active · admin password changed & `ADMIN_*` env removed · no debug endpoints/Swagger exposed publicly (or admin-only) · actuator restricted to health · secrets rotated from dev · Turnstile on.
**Legal:** all policy pages live · grievance officer/DMCA contact filled · cookie banner · lawyer review done.
**Ops:** backups running + restore tested · uptime monitors · Sentry receiving test error · DB indexes verified · Lighthouse mobile ≥ 85 on home/story/reader · sitemap submitted to Google Search Console & Bing · robots.txt correct · OG preview checked on WhatsApp/Twitter.
**Content:** ≥ 20 original stories seeded by you/invited writers so the site isn't empty.

## 7. Cost & scaling path
| Stage | Trigger | Action | Approx cost |
|---|---|---|---|
| Free | < ~5k MAU | as above | domain only |
| Grow 1 | DB > 0.5GB or cold starts hurt | Neon paid (~$19) or Supabase Pro; keep VM | ~$20–25/mo |
| Grow 2 | Frontend revenue / commercial | Vercel Pro or self-host / Cloudflare Pages | +$0–20 |
| Grow 3 | CPU/RAM bound | bigger VM / 2 backend instances behind LB (stateless ready) + Redis | $30–100 |
| Scale | search slow, feeds heavy | Meilisearch/Typesense, read replica, queue (RabbitMQ/SQS), CDN image resizing | varies |

## 8. Troubleshooting quick list
- **Cookies not sent:** check `Domain=.yourdomain`, `Secure`, `SameSite`, `credentials: 'include'`, CORS `allowCredentials`, same registrable domain.
- **CORS error:** exact origin match (scheme+host, no trailing slash).
- **OOM kills:** lower `-Xmx`, Hikari max 5, reduce Caffeine sizes, check `mem_limit`.
- **Flyway checksum error:** you edited an applied migration — revert and add a new migration.
- **Presigned upload fails:** R2 CORS rule, content-type mismatch, clock skew.
- **Emails in spam:** SPF/DKIM/DMARC, dedicated from-address, avoid spammy content.
- **Slow first request (Render):** cold start — ping monitor + ISR caching.
