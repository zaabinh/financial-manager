# Personal Finance Manager

A cross-platform personal finance application with a Spring Boot modular monolith and an Expo-powered React Native client for web, iOS, and Android.

## Tech Stack

- Java 21 LTS and Spring Boot 3
- Maven with Maven Wrapper
- PostgreSQL and Spring Data JPA
- Spring Security with JWT dependencies
- Flyway database migrations
- Lombok, MapStruct, and Bean Validation
- Docker and Docker Compose
- React Native with Expo for web, iOS, and Android

## Architecture

The backend uses feature-based modules with internal layers:

```text
Feature API -> Application -> Domain <- Infrastructure
```

Controllers remain transport-only, application services orchestrate use cases and transactions, domain code owns business rules, and infrastructure implements persistence and external integrations.

## Folder Structure

```text
financial-manager/
|-- apps/
|   |-- backend/          Spring Boot service
|   `-- mobile/           Expo React Native client
|-- docs/                 Product and engineering documentation
|-- docker/               Additional container assets
|-- scripts/              Development and deployment automation
|-- .env.example          Local Docker environment template
|-- docker-compose.yml
`-- README.md
```

## Prerequisites

- JDK 21
- Docker Desktop or Docker Engine with Docker Compose
- PostgreSQL 16 when running the backend outside Docker
- Node.js 22 LTS for the React Native client

No global Maven installation is required because the backend includes Maven Wrapper.

## Running Locally

1. Start PostgreSQL and create a database named `finance_manager_local`.
2. Set `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and a development `JWT_SECRET`.
3. Run the backend:

```powershell
cd apps/backend
./mvnw.cmd spring-boot:run
```

The default development profile expects Docker PostgreSQL at `localhost:5433` and serves the API on `http://localhost:8080`. Port `5433` avoids conflicts with a PostgreSQL installation already using the standard host port `5432`. Override connection values with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.

Email delivery requires an SMTP provider. Configure `MAIL_HOST`, `MAIL_PORT`, `MAIL_USERNAME`, `MAIL_PASSWORD`, `MAIL_SMTP_AUTH`, `MAIL_SMTP_STARTTLS`, `MAIL_FROM_ADDRESS`, and `MAIL_FROM_NAME`. Use an application-specific password when required by the provider, and keep credentials outside version control.

When the backend runs directly from Maven or an IDE, a root `.env` file is not loaded by Spring automatically. Export the `MAIL_*` values in the process environment or configure the IDE run configuration.

Email registration verification links use `CLIENT_PUBLIC_URL` and expire according to `EMAIL_VERIFICATION_EXPIRATION` (`PT24H` by default). Set `CLIENT_PUBLIC_URL` to the public React Native web origin outside local development.

4. Run the React Native client:

```powershell
cd apps/mobile
npm install
npm run web
```

Use `npm run android` with Android Studio or `npm run ios` on macOS with Xcode. See `apps/mobile/README.md` for API URL overrides and device networking.

## Docker Usage

Create a local environment file and replace all placeholder values:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Docker Compose exposes the backend on port `8080`, PostgreSQL on host port `5433`, and stores database data in the named `postgres_data` volume. Container-to-container traffic continues to use `postgres:5432`.

## Database Migrations

Flyway reads versioned migrations from `apps/backend/src/main/resources/db/migration`. A new empty database runs `V1` through `V12`. A database previously created by running `db.sql` is adopted once at baseline version `9`, reconciled by `V10`, aligned with the current user schema by `V11`, and upgraded with verification tokens by `V12`; see `docs/temp/guide.md` before enabling baseline-on-migrate.

## Configuration

The backend provides `dev`, `test`, and `prod` Spring profiles. Configuration uses environment variables, and no application secrets are committed. Production enables Hibernate schema validation so the JPA model must match the Flyway-managed schema.

## Future Roadmap

- Finalize product requirements and business rules
- Design and implement the PostgreSQL schema
- Implement authentication and user management
- Add accounts, categories, and transaction workflows
- Add budgets, notifications, dashboard, and analytics
- Expand the React Native client as backend feature modules become available
- Add observability, CI/CD, and production deployment automation

See `docs/project-roadmap.md` for the phase-level roadmap.
