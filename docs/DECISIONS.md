# Implementation decisions

- Work follows the phase-by-phase boundaries in `Copilot.md` and the build
  playbook; Phase 2 now implements the catalog, stories, chapters, and cover
  upload API. Later product features remain in their existing phases in
  `TASKS.md`.
- The playbook refers to `API_SPEC.md` and `UI_UX_SPEC.md`, but those files are
  not present in the supplied docs folder. Later API and UI phases should
  establish those contracts before implementing their features.
- The MinIO bucket is created manually in the local MinIO console as described
  in `README.md`; the MinIO client image could not be pulled in this
  environment, so automatic bucket initialization could not be verified.
- Phase 2's PostgreSQL/Testcontainers content integration test is authored but
  skipped locally: the available Docker endpoint rejects the client API
  version (1.32; it requires at least 1.40). The sanitizer and WebP unit tests
  run locally; execute the content integration test in CI or with compatible
  Docker.
- Backend `./mvnw verify` passes, but the PostgreSQL Testcontainers integration
  test is skipped here: the available Docker endpoint rejects the client API
  version (1.32; it requires at least 1.40). Run it in CI or with a compatible
  Docker installation to confirm the migrations and end-to-end auth flows.
- Password validation rejects a curated set of common passwords and obvious
  sequences. A full top-10k password list is not bundled because no vetted,
  redistributable source was provided.
- Avatar uploads accept JPEG and PNG only, validate the actual image, and
  re-encode it as JPEG to strip embedded metadata. WebP input support is not
  enabled until a safe decoder is selected.
- Phase 2 chapter HTML is sanitized with an allow-list; inline images are
  restricted to the configured public storage host and path. Cover uploads
  accept JPEG, PNG, or WebP up to 2 MiB and are normalized to WebP at no more
  than 800 pixels per dimension.
- Scheduled chapter publication uses ShedLock with PostgreSQL database time;
  Flyway `V6` adds its lock table and `V7` adds the unique story-slug index.
- The local database already contained a compatible ShedLock table while its
  Flyway history ended at `V5`; `V6` now creates the table only when absent so
  startup can adopt that existing lock table without dropping data.
- Phase 3 uses the generated OpenAPI types for public content and auth requests.
  The frontend reads the browser API from `NEXT_PUBLIC_API_URL` and server
  requests from `API_URL`.
- `/sitemap.xml` serves an XML sitemap index; individual public sitemap files
  contain at most 5,000 URLs and are generated from paginated published-story
  results.
- Access and refresh cookies use `Path=/` so the Next.js middleware can detect
  an existing session on protected page requests; API authorization still
  validates the signed access token and refresh-token state.
- Browse's `q` field filters only the currently loaded page. Full-text search
  and autocomplete remain Phase 7 work; the UI labels this limitation.
- Legal routes are draft placeholders and are excluded from indexing. They
  must be replaced with jurisdiction-reviewed content before public launch;
  this does not complete the Phase 9 legal/compliance work.
