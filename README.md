# NovelVerse

NovelVerse is a mobile-first web-novel platform. Its architecture and feature
requirements are documented in [`docs/`](docs/).

## Local development

Prerequisites: Java 21, Node.js 20+, pnpm 10+, and Docker Compose.

1. Copy the local-only development settings and start the dependencies:

   ```sh
   cp .env.example .env
   docker compose up -d
   ```

   PostgreSQL is available on port 5432, Redis on 6379, Mailpit at
   <http://localhost:8025>, and MinIO at <http://localhost:9001>. Sign in to
   the MinIO console with the local credentials in `.env`, then create a bucket
   named `novelverse`.

2. Start the backend in another terminal:

   ```sh
   export JWT_SECRET="$(openssl rand -base64 64)"
   export IP_HASH_SALT="$(openssl rand -hex 32)"
   cd backend
   ./mvnw spring-boot:run
   ```

   The API health check is at <http://localhost:8080/api/v1/health> and the
   Actuator health check is at <http://localhost:8080/actuator/health>. Flyway
   applies the database migrations at startup. Swagger UI is at
   <http://localhost:8080/swagger-ui/index.html>.

   For Google sign-in, set `GOOGLE_CLIENT_ID`. Avatar uploads require
   `S3_ENABLED=true` and the S3-compatible settings in `backend/.env.example`.
   For local MinIO, export `S3_ENDPOINT=http://localhost:9000`,
   `S3_REGION=us-east-1`, `S3_BUCKET=novelverse`, `S3_ACCESS_KEY=novelverse`,
   `S3_SECRET_KEY=novelverse_minio_dev_only`, and
   `S3_PUBLIC_BASE_URL=http://localhost:9000/novelverse`; create the bucket and
   allow public reads for the configured avatar URL.
   The backend requires a fresh `JWT_SECRET` and `IP_HASH_SALT` in each shell;
   never reuse the example credentials in production.

3. Start the frontend in another terminal:

   ```sh
   cd frontend
   cp .env.example .env.local
   pnpm install
   pnpm dev
   ```

   Open <http://localhost:3000>. The home, browse, story, and reader pages load
   public content from the backend API. The API health check remains available
   at <http://localhost:8080/api/v1/health>. Set
   `NEXT_PUBLIC_GOOGLE_CLIENT_ID` in `frontend/.env.local` to enable Google
   sign-in; leave it empty to show the unavailable-state message. Use **Log in**
   and **Sign up** in the header to access the auth forms.

   The Terms, Privacy, Content Policy, DMCA, Refund, and Contact pages are
   clearly marked drafts. They are not launch-ready legal advice and must be
   reviewed for the operating jurisdictions before public launch.

To stop the local services, run `docker compose down`. Add `-v` only if you
intend to delete the local PostgreSQL and MinIO data volumes.

## Checks

```sh
cd backend && ./mvnw verify
cd frontend && pnpm lint && pnpm test && pnpm build
```

The credentials in the root `.env.example` are for local development only.
Never use them outside a private local environment.
