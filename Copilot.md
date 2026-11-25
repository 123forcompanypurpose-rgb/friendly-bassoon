# CLAUDE.md — Rules for the AI coding agent (read before every task)

You are building **NovelVerse**, a web-novel platform. Docs live in `/docs`. They are the source of truth: `PRD.md`, `SRS.md`, `ARCHITECTURE.md`, `DATABASE_SCHEMA.md`, `API_SPEC.md`, `UI_UX_SPEC.md`, `SECURITY_AND_COMPLIANCE.md`, `TASKS.md`.

## Workflow rules
1. Work on **one phase / one task group at a time** from `docs/AI_BUILD_PLAYBOOK.md` and `docs/TASKS.md`. Do not build features from later phases.
2. Before coding: read the relevant docs, state your plan in ≤10 bullets, list files you will create/change.
3. After coding: build, run tests, run the app if possible, fix errors yourself. Never say "done" if it doesn't compile or tests fail.
4. Update `docs/TASKS.md` checkboxes. If you deviate from a doc, update the doc and explain why.
5. Make small, logical commits with conventional messages (`feat(auth): ...`). Never commit secrets or `.env`.
6. If requirements are ambiguous, make the simplest reasonable assumption, write it in `docs/DECISIONS.md`, and continue. Ask the human only for blocking items (credentials, domain names, business decisions).
7. Don't introduce new major dependencies/frameworks without need; prefer what `ARCHITECTURE.md` lists. Pin versions.

## Backend rules (Spring Boot 3, Java 21, Maven)
- Package-by-feature under `com.novelverse.<feature>` with `web` / `service` / `domain` sub-packages.
- Controllers: thin; use DTO records + Bean Validation; **never expose entities**; return proper HTTP codes; document with springdoc annotations.
- Services: `@Transactional` boundaries, business rules, authorization/ownership checks. Constructor injection only (Lombok `@RequiredArgsConstructor` OK).
- Repos: Spring Data JPA; avoid N+1 (use `@EntityGraph` or projections); all list endpoints paginated with max size 50.
- DB: **Flyway only**, `ddl-auto=validate`. Never edit an applied migration; add a new one. Counters updated with atomic SQL.
- Errors: global `@RestControllerAdvice` returning the standard error JSON with stable `code`.
- Security: deny by default; `@PreAuthorize`; sanitize all user HTML; rate limit sensitive endpoints; no secrets in code; no PII in logs.
- Config via `application.yml` + env vars (`${VAR:default}`); profiles `dev|test|prod`.
- Tests: JUnit5 + Testcontainers Postgres. Every service with business logic gets unit tests; every controller gets at least happy-path + 401/403/404/validation tests. Aim ≥70% on services.
- Style: records for DTOs, `Optional` for lookups, no `System.out`, use SLF4J. Format with Spotless (google-java-format). 

## Frontend rules (Next.js 15 App Router, TypeScript strict)
- Server Components by default; `"use client"` only where needed (interactivity). Fetch public data on the server with `revalidate`.
- API access **only** through the generated client in `src/lib/api` (regenerate from OpenAPI; never hand-write types that exist in the spec).
- UI with Tailwind + shadcn/ui; reuse components; mobile-first; dark mode via CSS variables.
- Forms: react-hook-form + zod; show server `fieldErrors`.
- No `any`; no unused code; ESLint + Prettier clean; accessibility labels on interactive elements.
- Every data view: loading skeleton, empty state, error state.
- Keep reader page lightweight (no heavy libs). Strings via next-intl (no hardcoded UI text in components).
- Env: only `NEXT_PUBLIC_*` for browser-safe values.

## Definition of Done (every task)
- [ ] Compiles; lint/format pass; tests pass (`./mvnw verify`, `pnpm lint && pnpm test && pnpm build`)
- [ ] Matches API_SPEC/DB schema; OpenAPI regenerated if API changed
- [ ] Security checklist items for the feature handled (authz, validation, sanitization, rate limit)
- [ ] Loading/empty/error states exist (frontend)
- [ ] TASKS.md updated; README/setup notes updated if commands changed

## Never do
- Never scrape or copy copyrighted novels/content for seed data (write original sample stories or use public-domain texts like Project Gutenberg with attribution).
- Never store tokens in localStorage. Never disable security features to "make it work".
- Never hardcode credentials/admin passwords. Never log secrets.
- Never use `ddl-auto=update/create`. Never return unbounded lists.

## Commands (keep this section updated)
- Infra: `docker compose up -d` (postgres, redis, mailpit, minio)
- Backend: `cd backend && ./mvnw spring-boot:run -Dspring-boot.run.profiles=dev` · tests `./mvnw verify`
- Frontend: `cd frontend && pnpm i && pnpm dev` · `pnpm gen:api` · `pnpm lint` · `pnpm test` · `pnpm build`
- E2E: `pnpm playwright test`
