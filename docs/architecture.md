# Application Architecture

## 1. Architecture Style

The target backend is a **modular monolith** organized **by feature**, with a **layered internal design** in each feature. It deploys as one Spring Boot application and uses one PostgreSQL database.

This style provides strong module boundaries and straightforward transactions without the operational cost of microservices. Modules can evolve independently inside one process and can be extracted only when scale, ownership, or reliability evidence justifies it.

## 2. Current-State Transition

The initial backend scaffold is package-by-layer (`controller`, `service`, `repository`, `entity`). It is transitional. Before business implementation, Phase 2 reorganizes placeholders into feature packages so new behavior is not built on the temporary structure.

No compatibility requirement exists for empty placeholder package names. Public compatibility is defined by the REST API and database migrations, not internal Java packages.

## 3. Target Package Structure

```text
com.example.financemanager
├── auth
│   ├── api
│   ├── application
│   ├── domain
│   └── infrastructure
├── user
├── account
├── category
├── transaction
├── budget
├── dashboard
├── analytics
├── notification
├── subscription          # reserved, inactive for MVP
└── shared
    ├── config
    ├── error
    ├── security
    ├── validation
    ├── mapping
    ├── logging
    └── persistence
```

Each active feature follows the same internal layers:

| Layer | Responsibility | May Depend On |
|---|---|---|
| `api` | REST controllers, request/response DTOs, transport validation, OpenAPI annotations. | Its application layer and shared transport/error types. |
| `application` | Use-case orchestration, authorization, transaction boundaries, domain coordination, DTO mapping. | Its domain abstractions, allowed module facades, shared abstractions. |
| `domain` | Entities, value objects, domain services, policies, calculations, repository interfaces. | Java and minimal shared domain primitives only. |
| `infrastructure` | JPA mappings/repositories, adapters, schedulers, security integration, external implementations. | Domain/application ports and framework libraries. |

Infrastructure implements inward-facing interfaces. Domain code does not import Spring MVC, JPA repositories, HTTP DTOs, or concrete security adapters.

## 4. Modules

| Module | Responsibilities | Published Application Interface |
|---|---|---|
| `auth` | Registration, email verification, authentication, access tokens, refresh rotation, logout, password changes. | Authentication commands and current-principal integration. |
| `user` | Profile, status, currency, timezone, ownership identity. | User profile queries and active-user checks. |
| `account` | Account lifecycle, initial balance, inclusion preferences, balance query. | Account ownership and balance services. |
| `category` | Default/user categories, type and lifecycle rules. | Applicable-category queries and validation. |
| `transaction` | Manual ledger entries, search, update/delete reconciliation. | Ledger commands and transaction query services. |
| `budget` | Monthly limits, usage, status, threshold transitions. | Budget commands, usage query, threshold evaluation. |
| `dashboard` | Current overview composition. | Dashboard query facade. |
| `analytics` | Category and monthly aggregations. | Read-only analytics facade. |
| `notification` | Settings, in-app messages, reminders, read state. | Notification commands and event handlers. |
| `subscription` | Reserved future entitlement and plan behavior. | No MVP interface. |
| `shared` | Cross-cutting technical primitives, not domain-specific business behavior. | Stable utilities and infrastructure conventions. |

Modules do not access another module's repositories or JPA entities directly. They use a narrow application facade, a domain port, or an in-process domain event.

## 5. Request Flow

```text
React Native Client (Web / iOS / Android)
        │ HTTPS + JSON + Bearer JWT
        ▼
Feature API Controller
        │ validated request DTO
        ▼
Application Service
        │ authorization + transaction boundary
        ▼
Domain Logic / Module Facades
        │ repository ports
        ▼
Infrastructure Adapters
        │ JPA / SQL
        ▼
PostgreSQL
```

Response mapping reverses the flow through application DTO mapping and the API response envelope. Controllers contain no balance, budget, token, or ownership logic.

## 6. Command and Query Responsibilities

- Commands mutate state through application services annotated with a single transaction boundary.
- Queries use read-only transactions and projection-oriented repositories when full domain hydration is unnecessary.
- Dashboard and analytics modules coordinate read models; they do not own ledger data.
- Financial calculations use `BigDecimal` and explicit rounding only where a presentation percentage requires it.
- User-local dates are resolved with the user's IANA timezone; timestamps are stored as UTC.

## 7. Transaction Boundaries

The following operations are atomic:

- registration plus default notification-settings creation;
- email-verification consumption plus user verification and sibling-token revocation;
- refresh-token rotation and replacement linkage;
- password change plus all-token revocation;
- transaction create/update/delete plus cached-balance maintenance;
- budget mutation plus initial usage evaluation;
- notification read-all updates.

Database triggers may provide defense in depth, but application transactions remain responsible for coherent use-case outcomes and API errors.

## 8. Module Communication

### 8.1 Direct Facades

Use synchronous application facades when the caller requires an immediate answer:

- transaction validates account and category access;
- dashboard queries account, transaction, budget, and notification projections;
- analytics queries transaction projections.

### 8.2 In-Process Events

Use events for secondary effects that should not create repository coupling:

- `TransactionRecorded`
- `TransactionChanged`
- `TransactionDeleted`
- `BudgetThresholdCrossed`
- `PasswordChanged`
- `UserDeleted`

For MVP, event handlers execute in-process. Financial ledger and balance changes remain synchronous and atomic. Notification creation may be after-commit with idempotency so a notification failure does not corrupt a financial transaction.

## 9. Shared Components

| Component | Responsibility |
|---|---|
| Security | JWT verification, principal model, role checks, password encoding, refresh-token utilities. |
| Error handling | Stable error codes, field errors, correlation IDs, response envelope. |
| Validation | Reusable syntactic constraints; domain validation remains in feature modules. |
| Auditing | Created/updated timestamps and authenticated actor where required. |
| Logging | Structured sanitized logging and request correlation. |
| Mapping | MapStruct conventions between API/application/domain shapes. |
| Configuration | Typed environment-backed settings and profile configuration. |
| Persistence | Base auditing support, UUID conventions, transaction helpers. |

The `shared` package must not become a dumping ground. A component belongs there only when at least two modules need the same technical abstraction and it contains no feature ownership.

## 10. Security Architecture

1. A security filter validates the access-token signature, issuer, audience, and expiration.
2. The filter creates a minimal authenticated principal containing user UUID and role.
3. Endpoint rules enforce public, authenticated, or admin access.
4. Application services enforce active status and resource ownership.
5. Repositories scope owned queries by both resource UUID and user UUID.
6. PostgreSQL constraints/triggers protect critical relationship integrity.

This layered model prevents authorization from depending solely on URL configuration.

## 11. Persistence Architecture

- PostgreSQL is the system of record.
- Flyway is the only production schema migration mechanism.
- JPA entities are infrastructure persistence models when separation from rich domain objects is useful.
- Repository interfaces belong inward; Spring Data implementations belong in infrastructure.
- The transaction ledger is authoritative for balances and analytics.
- A physical `current_balance` value is a monitored cache and cannot be written by API clients.
- Read-heavy analytics may use SQL projections or database views without exposing them as mutable domain entities.

## 12. API and DTO Boundaries

- Request and response DTOs are feature-owned and versioned by the `/api/v1` contract.
- Persistence entities never cross the controller boundary.
- MapStruct performs structural mapping; application services provide context-dependent values.
- Sensitive fields are absent by type, not merely ignored at serialization time.
- Enum wire values are stable uppercase strings documented in the API contract.

## 13. Architectural Principles

- **Single Responsibility:** each module owns one cohesive business capability.
- **Dependency Inversion:** domain/application code defines ports; infrastructure implements them.
- **Encapsulation:** module data is accessed through published interfaces, not foreign repositories.
- **Thin Controllers:** transport concerns only.
- **Explicit Transactions:** one use case defines one clear transaction boundary.
- **DTO Separation:** API, domain, and persistence shapes may evolve independently.
- **Defense in Depth:** service ownership checks plus database integrity controls.
- **Evolutionary Design:** optimize the modular monolith before extracting services.

## 14. Observability

- Every request receives or propagates a correlation ID.
- Structured logs include module, operation, outcome, duration, and correlation ID.
- Metrics include request latency/error rates, authentication failures, refresh reuse, database pool state, budget-notification failures, and balance-reconciliation discrepancies.
- Health endpoints separate liveness and readiness.
- Traces exclude passwords, tokens, financial notes, and unnecessary personal data.

## 15. Future Evolution

Extraction is considered only when measurements or organizational ownership justify it:

- **Notification worker:** when reminder/alert volume requires independent scheduling or retries.
- **Analytics worker/read store:** when aggregation load harms transactional performance.
- **Payment/subscription service:** only after subscription purchasing enters approved scope and compliance boundaries are understood.

Extraction requires a stable module interface, independent scaling/reliability need, ownership, observability, and a migration plan. Database-table presence alone is not justification.

## 16. Architecture Verification

Automated architecture tests should enforce:

- domain packages do not depend on API or infrastructure;
- feature modules do not import another feature's infrastructure or repositories;
- controllers depend on application services, not repositories;
- subscription code is not reachable from MVP endpoints;
- shared code does not depend on feature packages.
