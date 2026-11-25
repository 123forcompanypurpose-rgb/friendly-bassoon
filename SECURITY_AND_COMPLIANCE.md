# SECURITY & COMPLIANCE

> This is an engineering checklist, **not legal advice**. Before public launch have a lawyer review Terms, Privacy, DMCA, and (if you earn money) tax/payout rules for India and your target countries.

## 1. Application security checklist
| Area | Requirement |
|---|---|
| Passwords | BCrypt cost ≥ 12 (or Argon2id); min length 8; reject top-10k common passwords; never log |
| Tokens | JWT access 15m signed HS512/RS256 with ≥ 256-bit secret from env; refresh tokens opaque, hashed, rotating, reuse detection |
| Cookies | `HttpOnly; Secure; SameSite=Lax; Path=/; Domain=.yourdomain` |
| CSRF | Custom header requirement + SameSite; GET never changes state |
| CORS | Allow-list only the web origin(s), `allowCredentials=true`, no wildcard |
| XSS | Sanitize chapter/comment/blurb HTML server-side (OWASP Java HTML Sanitizer allow-list: p,br,strong,em,u,h2,h3,blockquote,ul,ol,li,hr,a[href rel=nofollow noopener],img[src from our CDN only]). Comments/reviews: plain text only, escape on render. CSP header on Next.js (no inline scripts except nonce) |
| SQL injection | Only parameterized queries/JPA; no string concatenation in native queries |
| AuthZ | Method-level `@PreAuthorize` + ownership checks in service (never trust IDs from client); tests for IDOR on every `{id}` endpoint |
| Rate limiting | Bucket4j per IP+user: login 5/min, register 5/hour/IP, forgot-password 3/hour, comment 10/min, report 10/hour, search 60/min, upload 10/hour, general 300/min |
| Brute force | Account lock 15 min after 5 fails; generic error messages (no user enumeration) |
| Captcha | Cloudflare Turnstile on register, forgot-password, DMCA, report (guests) |
| File uploads | Presigned PUT with content-type & size limits; verify magic bytes on confirm; re-encode to WebP and strip EXIF (privacy); random keys; separate public bucket for images only |
| SSRF | Never fetch user-supplied URLs server-side |
| Headers | HSTS, X-Content-Type-Options, X-Frame-Options DENY, Referrer-Policy, Permissions-Policy |
| Secrets | Env vars only; `.env` in `.gitignore`; GitHub secret scanning on; rotate on leak |
| Dependencies | Dependabot + `mvn dependency-check` / `npm audit` in CI |
| Logging | No passwords/tokens/PII; hash IPs (SHA-256 + salt) when stored |
| Admin | 2FA for admin (P2), IP allow-list optional, all actions audit-logged |
| Payments | Verify webhook signatures, idempotency, server-side price lookup, never trust client amounts |
| Backups | Daily `pg_dump` → R2 (encrypted), 14-day retention, restore drill before launch |

## 2. Content policy (publish as `/legal/content-policy`)
Allowed: original fiction/poetry/non-fiction by the uploader.
Prohibited (immediate removal + ban where applicable):
1. **Any sexual content involving minors** (zero tolerance; report to authorities as required by law).
2. Copyright infringement / copied works / unauthorized translations.
3. Non-consensual sexual content, extreme gore glorifying real violence, terrorism/extremist propaganda.
4. Hate speech, targeted harassment, doxxing, threats.
5. Spam, scams, phishing links, mass-posted promos.
6. Real-person defamation; impersonation.
7. Unlabeled AI-generated bulk content (decide policy: require "AI-assisted" label; ban pure AI spam).
Mature content (explicit sex/violence) must be flagged `MATURE`, is hidden from guests/under-18s, excluded from sitemap/OG previews, and `noindex`.

## 3. Moderation workflow
1. Report created → priority (COPYRIGHT/SEXUAL_MINORS = high).
2. Moderator reviews within SLA (high: 24h, normal: 72h).
3. Action: dismiss / remove (content hidden, `removed_reason` set, author notified with appeal link) / warn / suspend N days / ban.
4. Strike system: 3 confirmed violations → ban. Repeat copyright infringers terminated.
5. Appeals: email form, reviewed by a different moderator, logged.
6. Everything in `moderation_actions` + `audit_logs`.
Automated helpers (P1): bad-word/spam heuristics on comments, link-count limit for new accounts, duplicate-content detection (hash of first 500 chars of chapters) to flag copy-paste across accounts.

## 4. Copyright / DMCA
- Publish designated-agent contact + `/dmca` form (claimant info, identification of work & infringing URL, good-faith & accuracy statements, signature).
- On valid notice: remove/disable promptly, notify uploader, allow counter-notice, restore if no suit filed within the statutory window (consult a lawyer for exact periods).
- Repeat-infringer policy stated in ToS. Keep records.
- India: operate as an "intermediary"; follow IT Rules (grievance officer name/contact displayed, acknowledge complaints in 24h, resolve in 15 days — verify current rules).

## 5. Privacy & data protection
- Privacy Policy: what is collected (email, username, IP hash, reading activity, device), why, retention, sharing (processors: hosting, email, payments), rights.
- Compliance targets: **India DPDP Act 2023**, **GDPR/UK GDPR** for EU/UK users, CCPA basics.
- Features required: consent at signup (checkbox for ToS+Privacy), cookie banner for non-essential cookies (analytics), data export (`/me/export` JSON), account deletion, marketing opt-in default OFF, one-click unsubscribe.
- Children: require 18+ for mature; minimum age 13 (16 in some EU countries) — state in ToS; no targeted ads to minors; collect DOB only for age gate and store minimal.
- Data minimization; encrypt in transit (TLS) and at rest (provider default).
- Breach plan: document who to notify and within what time (72h under GDPR; DPDP requires notifying the Board and users).

## 6. Payments & financial compliance (Phase 12)
- Razorpay/Stripe need: business details, website with Terms, Privacy, Refund/Cancellation policy, Contact info, product description, pricing. Prepare before applying (KYC can take days).
- Coins are virtual goods: state "non-refundable, no cash value" in ToS (check consumer-law limits).
- GST on coin sales in India (and VAT/sales tax abroad) — consult an accountant; invoices for purchases.
- Writer payouts: collect PAN/tax details, TDS rules, minimum payout threshold, payout schedule, fraud holds. Start with manual payouts.
- Keep immutable ledger and reconciliation report (payments vs coin ledger) monthly.
- Never store card data (providers handle PCI).

## 7. Abuse scenarios to test
Account enumeration · IDOR on drafts/premium chapters · XSS in chapter HTML and comments · stored XSS via profile bio · oversized uploads · webhook replay · negative/duplicate coin unlock race · mass-follow/comment spam · view-count inflation · scraping (rate limit + robots, consider Cloudflare bot fight mode) · open redirect in `?next=`.

## 8. Required legal pages (create before launch)
Terms of Service · Privacy Policy · Content Policy · DMCA/Copyright Policy · Refund & Cancellation Policy · Cookie Policy · Community Guidelines · Contact + Grievance Officer · About.
