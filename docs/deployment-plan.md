# Deployment Plan

## 1. Deployment Objectives

- Reproduce the same application artifact across environments.
- Apply database changes safely and exactly once.
- Keep secrets outside source control and container images.
- Provide health, logs, metrics, backup, and rollback from the first production release.
- Keep the beginner path simple while preserving a route to stronger operations.

## 2. Environments

| Environment | Purpose | Database | Deployment |
|---|---|---|---|
| `local` | Developer iteration | Local/Compose PostgreSQL | Maven or Docker Compose |
| `test` | Automated isolated tests | PostgreSQL Testcontainers | CI runner |
| `staging` | Production-like verification | Separate managed PostgreSQL | Container platform |
| `production` | Real user workload | Managed PostgreSQL with backups | Container platform with HTTPS |

Staging and production never share databases, secrets, object storage, or JWT signing keys.

## 3. Local Development

Components:

- JDK 21 and Maven Wrapper.
- Spring Boot development profile.
- PostgreSQL 16.
- Flyway migrations.
- Docker Compose for backend and database when desired.

Expected flow:

1. Copy `.env.example` to an untracked `.env`.
2. Set a local database password and long random JWT secret.
3. Start services with `docker compose up --build`, or start PostgreSQL then run Maven.
4. Flyway applies migrations on backend startup.
5. Verify readiness and run tests before submitting changes.

Local data is disposable. Developers must not use production data dumps.

## 4. Container Design

### 4.1 Backend Image

- Multi-stage build using a pinned Java 21 JDK builder and JRE runtime.
- Maven dependencies cached before source compilation.
- Tests run in CI before image creation; image build packages the verified commit.
- Runtime uses a nonroot user.
- Only the application JAR and required runtime files are copied.
- Port `8080` is exposed.
- Image tag includes immutable commit SHA; release tags are additional aliases.
- JVM receives container-aware memory settings from the platform.

### 4.2 PostgreSQL Container

Local Compose uses PostgreSQL 16, port `5432`, a named data volume, and `pg_isready` health checks. Production uses managed PostgreSQL rather than a database container on the application host.

### 4.3 Compose Dependencies

Backend starts only after PostgreSQL reports healthy. Application readiness remains false until database connectivity and migrations are valid.

## 5. Configuration

Canonical runtime variables:

| Variable | Required | Example Meaning |
|---|---:|---|
| `SPRING_PROFILES_ACTIVE` | Yes | `staging` or `production` |
| `SERVER_PORT` | No | Defaults to `8080` |
| `DATABASE_URL` | Yes | PostgreSQL JDBC URL |
| `DATABASE_USERNAME` | Yes | Least-privilege application user |
| `DATABASE_PASSWORD` | Yes | Database secret |
| `JWT_SECRET` | Yes | At least 256-bit random HMAC key |
| `JWT_ACCESS_EXPIRATION` | Yes | `PT15M` or equivalent typed duration |
| `JWT_REFRESH_EXPIRATION` | Yes | `P7D` or equivalent typed duration |
| `ALLOWED_ORIGINS` | Yes where web access exists | Comma-separated allow-list |
| `CLIENT_PUBLIC_URL` | Yes | HTTPS React Native web origin used in verification links |
| `EMAIL_VERIFICATION_EXPIRATION` | No | Defaults to `PT24H` |
| `MAIL_HOST` / `MAIL_PORT` | Yes for verification | Transactional SMTP endpoint |
| `LOG_LEVEL_ROOT` | No | Defaults to `INFO` |

Spring configuration, Compose, and `.env.example` use these canonical names. Datasource credentials and JWT secrets are required environment values with no application defaults. Production email verification requires an authenticated SMTP provider and a public HTTPS `CLIENT_PUBLIC_URL`.

Secrets are supplied by the hosting platform's secret manager. They are not committed, printed by CI, passed as Docker build arguments, or stored in image labels.

## 6. CI/CD Pipeline

GitHub Actions pipeline:

1. **Checkout** the exact commit.
2. **Set up Java 21** with Maven dependency caching.
3. **Static checks** for formatting, compilation, architecture, dependency issues, and secrets.
4. **Run tests**: unit, slice, PostgreSQL migration/repository, integration, and security.
5. **Build JAR** with Maven Wrapper.
6. **Generate/compare OpenAPI** for breaking changes.
7. **Build Docker image** tagged with commit SHA.
8. **Scan** dependencies and image for known vulnerabilities.
9. **Push image** only from protected branches/tags after checks pass.
10. **Deploy staging** using immutable image digest.
11. **Run Flyway and health checks** with staging smoke tests.
12. **Approve production** through protected environment controls.
13. **Deploy production** using rolling or replacement strategy.
14. **Verify health and key synthetic requests**; automatically halt/rollback on failure.

CI uses short-lived cloud credentials through workload identity where supported.

## 7. Database Migrations

- Flyway migrations are versioned and reviewed with application changes.
- Applied migrations are never edited.
- A deployment runs migrations before new instances receive traffic.
- A legacy database created manually from `db.sql` is baselined once at version 9, reconciled by `V10`, aligned with the current user schema by `V11`, and upgraded with verification tokens by `V12`; baseline-on-migrate is disabled immediately afterward.
- Only one migration process runs at a time through Flyway locking/platform job control.
- Destructive changes require a verified backup and expand-and-contract rollout.
- Rollback uses a previous application image only when schema remains backward-compatible.
- Schema defects are fixed by a forward migration.
- Staging rehearses production-size or representative migration timing.

## 8. Production Topology

```text
Internet
   │ HTTPS
   ▼
Managed TLS / Reverse Proxy
   │
   ▼
Spring Boot Container(s)
   │ private TLS/network
   ▼
Managed PostgreSQL
```

Initial deployment may use one backend instance if the provider restarts failed containers. The application remains stateless so a second instance can be added without session migration.

## 9. Health and Readiness

Expose Spring Boot Actuator:

- `/actuator/health/liveness`: process can continue running; must not depend on external services.
- `/actuator/health/readiness`: database connectivity, migration validity, and essential configuration are ready.
- Metrics endpoint is restricted to platform monitoring.

Health responses expose no secrets, SQL, host topology, or detailed exception messages publicly.

## 10. Observability

### Logs

- Structured JSON to stdout.
- Platform aggregates and retains logs.
- Correlation IDs connect edge, application, and error events.
- Auth tokens, passwords, financial notes, and datasource secrets are redacted.

### Metrics and Alerts

- HTTP request rate, p50/p95/p99 latency, and error status.
- JVM memory/GC, thread pools, database pool usage.
- Database connection and query latency.
- Login failures, rate limiting, token reuse, authorization failures.
- Flyway failure and readiness failure.
- Budget/reminder job failures.
- Cached-balance reconciliation discrepancy.

Alerts route to an owned notification channel with severity and runbook links.

## 11. Backup and Restore

- Managed automated backups run at least daily.
- Point-in-time recovery is enabled when supported and affordable.
- Target RPO is 24 hours; target RTO is 4 hours for MVP.
- Backup retention follows privacy and cost policy.
- Restore tests run at least quarterly in an isolated environment.
- Before destructive migrations, create and verify an on-demand backup.
- Restoration includes database, application image version, configuration, and post-restore reconciliation.

## 12. Deployment and Rollback

### Normal Release

1. Deploy tested image to staging.
2. Apply migrations and smoke-test.
3. Record release version, image digest, and migration version.
4. Deploy production with readiness gating.
5. Run synthetic register/login or nonmutating authenticated health workflow.
6. Monitor elevated error/latency metrics.

### Rollback

- If no incompatible migration ran, redeploy the previous image digest.
- If an expand migration ran, previous and new versions remain compatible.
- If a data/schema defect occurred, stop traffic-changing operations, restore when necessary, and roll forward with a corrective migration.
- Rotate exposed secrets and revoke sessions when security is involved.

## 13. Security and Network Controls

- HTTPS is mandatory in staging and production.
- Database is not publicly exposed; only application/network administration paths may connect.
- Database application user has no superuser or schema-owner privileges beyond migration strategy needs.
- Separate migration credentials may be used in mature environments.
- Security headers, request-size limits, CORS allow-list, and rate limiting are enabled at application or edge.
- Container runs nonroot with read-only filesystem where platform support permits.

## 14. Beginner-Friendly Deployment Path

A simple first deployment:

1. Choose a container platform such as Render, Railway, Fly.io, or an equivalent service.
2. Provision managed PostgreSQL in the same region.
3. Connect the GitHub repository or publish the CI-built image.
4. Configure canonical environment variables in platform secret storage.
5. Configure health checks and HTTPS.
6. Deploy to staging, verify Flyway and smoke tests, then create a separate production service/database.
7. Enable managed backups, basic metrics, and log retention.

No provider is mandatory. Selection should compare regional availability, managed PostgreSQL quality, backup/restore, pricing, secret handling, observability, and straightforward container rollback.

## 15. Production Readiness Checklist

- Java 21 tests and security scans pass.
- Canonical environment names are implemented; no hard-coded password remains.
- Docker image is pinned, nonroot, scanned, and identified by digest.
- Flyway passes from empty and previous production schema.
- HTTPS, CORS, rate limits, and secret manager are configured.
- Readiness/liveness and alerts are verified.
- Backup exists and restore procedure has been tested.
- Previous image and rollback instructions are available.
- API smoke tests and balance reconciliation pass after deployment.
