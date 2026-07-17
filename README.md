# Personal Finance Manager

A production-oriented foundation for a personal finance platform. The repository currently contains a Spring Boot backend skeleton and documentation placeholders; the Flutter mobile client will follow in a later phase.

## Tech Stack

- Java 21 LTS and Spring Boot 3
- Maven with Maven Wrapper
- PostgreSQL and Spring Data JPA
- Spring Security with JWT dependencies
- Flyway database migrations
- Lombok, MapStruct, and Bean Validation
- Docker and Docker Compose
- Flutter mobile client (planned)

## Architecture

The backend uses a layered architecture:

```text
Controller
    |
Service
    |
Repository
    |
PostgreSQL
```

The initial packages also reserve clear boundaries for DTOs, mapping, security, validation, configuration, utilities, constants, and centralized exception handling.

## Folder Structure

```text
financial-manager/
|-- apps/
|   |-- backend/          Spring Boot service
|   `-- mobile/           Flutter placeholder
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

No global Maven installation is required because the backend includes Maven Wrapper.

## Running Locally

1. Start PostgreSQL and create a database named `finance_manager`.
2. Set `DATABASE_USERNAME`, `DATABASE_PASSWORD`, and a development `JWT_SECRET`.
3. Run the backend:

```powershell
cd apps/backend
./mvnw.cmd spring-boot:run
```

The default development profile expects PostgreSQL at `localhost:5432` and serves the API on `http://localhost:8080`. Override connection values with `DATABASE_URL`, `DATABASE_USERNAME`, and `DATABASE_PASSWORD`.

## Docker Usage

Create a local environment file and replace all placeholder values:

```powershell
Copy-Item .env.example .env
docker compose up --build
```

Docker Compose exposes the backend on port `8080`, PostgreSQL on port `5432`, and stores database data in the named `postgres_data` volume.

## Database Migrations

Flyway reads versioned migrations from `apps/backend/src/main/resources/db/migration`. A new empty database runs `V1` through `V10`. A database previously created by running `db.sql` is adopted once at baseline version `9`, then reconciled by `V10`; see `docs/temp/guide.md` before enabling baseline-on-migrate.

## Configuration

The backend provides `dev`, `test`, and `prod` Spring profiles. Configuration uses environment variables, and no application secrets are committed. Production enables Hibernate schema validation so the JPA model must match the Flyway-managed schema.

## Future Roadmap

- Finalize product requirements and business rules
- Design and implement the PostgreSQL schema
- Implement authentication and user management
- Add accounts, categories, and transaction workflows
- Add budgets, notifications, dashboard, and analytics
- Build the Flutter mobile client
- Add observability, CI/CD, and production deployment automation

See `docs/project-roadmap.md` for the phase-level roadmap.
