# Project Roadmap

## Roadmap Principles

- Complete and verify one phase before depending on it.
- Keep the backend a modular monolith throughout the MVP.
- Treat the REST contract, Flyway migrations, and business rules as reviewed interfaces.
- Deliver thin vertical slices after foundation work rather than building every persistence layer first.
- Keep subscriptions, payments, recurring transactions, imports, email, and push outside the MVP.

## Phase 1: Planning

**Objectives**

- Establish one coherent product and engineering specification.
- Validate core UX before implementation.

**Tasks**

- Complete requirements, business rules, use cases, API, security, architecture, testing, deployment, and roadmap documents.
- Review the draft SQL against the documented MVP.
- Produce ERD and Flutter wireframes for auth, home, accounts, transactions, budgets, analytics, notifications, and settings.
- Resolve terminology: Account, Total Balance, Monthly Savings, Remaining Budget, and `SAVINGS`.

**Deliverables**

- Approved documentation set, ERD, API contract outline, and wireframes.

**Learning outcomes**

- Requirements traceability, domain modeling, REST semantics, and personal-finance terminology.

**Definition of done**

- No unresolved contradiction exists among requirements, rules, schema, API, use cases, and wireframes; stakeholders approve MVP/out-of-scope boundaries.

**Dependencies**

- Product goals and initial UI concept.

**Risks**

- Overdesigning future capabilities; inconsistent SQL and API terminology. Mitigation: mark dormant fields explicitly and maintain one glossary.

## Phase 2: Project Setup

**Objectives**

- Turn the scaffold into a safe, runnable modular foundation.

**Tasks**

- Reorganize package-by-layer placeholders into feature packages with `api/application/domain/infrastructure`.
- Align account enum from `SAVING` to `SAVINGS`.
- Align configuration to `DATABASE_*` and canonical JWT expiration variables.
- Remove hard-coded development credentials.
- Configure Spring profiles, typed configuration, global errors, response envelope, validation, MapStruct, logging correlation, and Actuator.
- Convert the draft schema into reviewed Flyway migrations and PostgreSQL Testcontainer tests.
- Finalize Docker Compose and CI baseline.

**Deliverables**

- Java 21 build, modular package structure, migrated database, Compose environment, CI workflow, health endpoints, and architecture tests.

**Learning outcomes**

- Spring Boot configuration, Flyway, Docker, modular boundaries, and CI fundamentals.

**Definition of done**

- Clean checkout builds and tests with Maven Wrapper; Compose starts; Flyway succeeds; no secret is committed; architecture tests enforce boundaries.

**Dependencies**

- Phase 1 contracts.

**Risks**

- Building logic on temporary packages; migration drift from `db.sql`; environment mismatch. Mitigation: complete alignment before feature code.

## Phase 3: Authentication

**Objectives**

- Establish secure identity and session lifecycle.

**Tasks**

- Implement registration and default notification settings.
- Implement BCrypt cost 12 password hashing and policy.
- Implement login and 15-minute HMAC-SHA-256 JWT access tokens.
- Implement opaque seven-day refresh tokens, SHA-256 hashes, rotation, family reuse detection, logout, and logout-all.
- Implement password change and all-session revocation.
- Add rate limiting, sanitized audit events, security filters, and tests.

**Deliverables**

- Authentication APIs, security configuration, token persistence, and complete auth test suite.

**Learning outcomes**

- Spring Security, JWT validation, refresh-token state, password security, and attack-aware testing.

**Definition of done**

- UC-001–003 and UC-014 pass; expired/revoked/reused tokens and inactive users are rejected; no sensitive values appear in logs/responses.

**Dependencies**

- Users and refresh-token migrations, global error contract.

**Risks**

- Token race conditions, weak secrets, account enumeration. Mitigation: locking, rotation tests, stable errors, secret validation.

## Phase 4: Core Master Data

**Objectives**

- Implement user profiles, accounts, and categories with strict ownership.

**Tasks**

- Implement profile read/update/delete and currency/timezone settings.
- Implement account CRUD, activation, initial-balance handling, and inclusion preference.
- Implement default category seed data and user category lifecycle.
- Implement scoped repository queries and cross-user authorization tests.
- Expose OpenAPI-documented endpoints and Flutter data adapters.

**Deliverables**

- User, account, and category modules with tests and API documentation.

**Learning outcomes**

- JPA ownership patterns, soft deletion, DTO mapping, validation, and REST lifecycle design.

**Definition of done**

- FR-USER, FR-SET, FR-ACC, and FR-CAT P0 requirements pass; UC-004 and UC-015 pass; cross-user tests cover every endpoint.

**Dependencies**

- Phase 3 authenticated principal and Phase 2 schema.

**Risks**

- Exposing JPA entities, ambiguous deletes, multi-currency totals. Mitigation: response DTOs, explicit lifecycle policies, currency grouping.

## Phase 5: Transactions

**Objectives**

- Deliver the authoritative manual financial ledger.

**Tasks**

- Implement create, view, update, soft delete, search, filters, pagination, and sorting.
- Enforce account/category ownership, active state, and type compatibility.
- Implement atomic old/new contribution reconciliation.
- Implement derived balance query and monitored cache maintenance/reconciliation.
- Add Flutter transaction entry/history flows.

**Deliverables**

- Transaction module, balance service, reconciliation query/metric, and mobile transaction experience.

**Learning outcomes**

- Transaction boundaries, exact decimal arithmetic, temporal queries, indexing, and correction workflows.

**Definition of done**

- UC-005–008 pass; balance formula matches ledger after create/update/delete; cache reconciliation reports zero discrepancy; performance targets pass on representative data.

**Dependencies**

- Active accounts/categories and authenticated ownership.

**Risks**

- Double-applied reversals, balance drift, date/timezone errors. Mitigation: atomic tests, fixed clocks, ledger reconciliation.

## Phase 6: Dashboard and Analytics

**Objectives**

- Turn transaction data into clear current and historical insights.

**Tasks**

- Implement Total Balance, current-month income/expense, Monthly Savings, and recent transactions.
- Implement selected-month summary.
- Implement category expense, income-versus-expense, and Monthly Savings series.
- Enforce bounded ranges and currency-safe aggregation.
- Add Flutter dashboard cards and charts.

**Deliverables**

- Dashboard/analytics APIs, optimized projections, charts, and performance tests.

**Learning outcomes**

- SQL aggregation, projections, time-series modeling, chart data contracts, and query tuning.

**Definition of done**

- UC-010–011 pass; empty periods return zeros; calculations reconcile with ledger; p95 analytics meets the documented target.

**Dependencies**

- Phase 5 transaction ledger and account inclusion rules.

**Risks**

- Slow aggregations, currency ambiguity, boundary errors. Mitigation: indexes, query plans, explicit filters, timezone tests.

## Phase 7: Budgets and Notifications

**Objectives**

- Help users control monthly category spending through usage status and in-app alerts.

**Tasks**

- Implement monthly budget CRUD, uniqueness, usage, remaining amount, and status.
- Implement warning/exceeded transition detection and idempotency.
- Implement notification settings, daily reminder scheduler, list/count/read/read-all/delete.
- Keep nonmonthly periods, rollover, email, push, and quiet hours disabled.
- Add Flutter budget and notification screens.

**Deliverables**

- Budget and notification modules, scheduler, mobile screens, and threshold integration tests.

**Learning outcomes**

- Scheduled work, idempotency, threshold state, user timezone execution, and notification UX.

**Definition of done**

- UC-009, UC-012, and UC-013 pass; duplicate budgets fail; warning/exceeded notifications occur once; disabled settings suppress generation.

**Dependencies**

- Transactions, categories, user timezone, security.

**Risks**

- Duplicate alerts, missed timezone jobs, transaction edits changing thresholds. Mitigation: idempotency keys/state tests and recalculation policy.

## Phase 8: Flutter Mobile Application

**Objectives**

- Complete a cohesive production-ready mobile experience for every MVP flow.

**Tasks**

- Finalize Dio client, response/error mapping, secure token storage, coordinated refresh, and GoRouter guards.
- Implement register/login/logout, home, accounts, categories, transaction entry/history, budgets, analytics, notifications, profile, and settings.
- Use Provider or Riverpod consistently; select one before feature implementation.
- Add accessibility, loading, empty, offline-error, and retry states.
- Validate exact decimal input and localized date/time display.

**Deliverables**

- Signed development builds and complete MVP mobile feature set.

**Learning outcomes**

- Flutter state management, secure storage, navigation, API integration, responsive UI, and charting.

**Definition of done**

- All 15 use cases work on supported Android/iOS test devices; no token uses insecure storage; error and empty states are usable.

**Dependencies**

- Stable v1 API and completed backend modules.

**Risks**

- Refresh races, state-management inconsistency, decimal loss. Mitigation: one auth coordinator, one state approach, decimal-safe types/tests.

## Phase 9: Testing and Quality

**Objectives**

- Raise confidence from feature-complete to release-ready.

**Tasks**

- Complete unit, slice, repository, integration, security, architecture, contract, Flutter, and system tests.
- Reach practical coverage goals for critical logic.
- Run static analysis, dependency/secret/container scans.
- Load-test CRUD, dashboard, analytics, and login/refresh limits.
- Review OpenAPI, migration upgrade, accessibility, logs, and privacy.

**Deliverables**

- Release test report, security findings report, performance baseline, migration rehearsal, and approved release candidate.

**Learning outcomes**

- Risk-based testing, security verification, performance analysis, and release governance.

**Definition of done**

- Testing-strategy release acceptance passes; no unaccepted critical/high finding; staging meets latency, integrity, and smoke criteria.

**Dependencies**

- Feature-complete backend and Flutter app.

**Risks**

- Late discovery of architectural/security defects. Mitigation: execute tests continuously in earlier phases, then use this phase for closure.

## Phase 10: Deployment

**Objectives**

- Launch safely and operate the MVP.

**Tasks**

- Provision staging/production container services and separate managed PostgreSQL databases.
- Configure HTTPS, canonical secrets, health checks, logs, metrics, alerts, and rate limiting.
- Implement protected GitHub Actions build/scan/push/deploy pipeline.
- Rehearse Flyway, backup/restore, rollback, and incident procedures.
- Deploy staging, run smoke tests, approve production, and monitor rollout.

**Deliverables**

- Live production service, CI/CD pipeline, dashboards/alerts, backups, runbooks, and release record.

**Learning outcomes**

- Cloud deployment, operational security, observability, database recovery, and incident readiness.

**Definition of done**

- Production readiness checklist passes; application is healthy over HTTPS; backup and rollback are verified; post-deploy reconciliation and smoke tests pass.

**Dependencies**

- Phase 9 approved release candidate.

**Risks**

- Migration failure, secret/config errors, provider limitations, unexpected cost. Mitigation: staging rehearsal, immutable images, managed backups, quotas, and rollback.

## Post-MVP Candidates

These require separate product approval and architecture updates:

- subscription plans and subscription management;
- payment processing;
- recurring transactions and imports;
- bank synchronization;
- email and push notifications;
- automatic currency conversion;
- shared accounts;
- advanced or AI-assisted analytics.
