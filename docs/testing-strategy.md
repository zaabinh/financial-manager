# Testing Strategy

## 1. Objectives

Testing protects financial correctness, ownership isolation, authentication safety, schema integrity, and stable API behavior. The strategy favors fast domain tests, focused Spring slices, PostgreSQL-backed persistence tests, and a smaller number of end-to-end flows.

## 2. Test Pyramid

| Layer | Purpose | Typical Tools |
|---|---|---|
| Unit | Domain calculations, policies, application branching. | JUnit 5, Mockito, AssertJ |
| Slice | Repository mappings/queries and controller HTTP behavior. | `@DataJpaTest`, MockMvc, Spring Security Test, Testcontainers |
| Integration | Complete use cases through real Spring configuration and PostgreSQL. | `@SpringBootTest`, MockMvc, Testcontainers |
| Contract | OpenAPI and response-schema compatibility. | OpenAPI validation/diff tooling |
| Client | React Native units, components, navigation, API adapters, and platform behavior. | Jest, React Native Testing Library, MSW, Maestro/Detox |
| System | Deployed API, database migration, and mobile smoke paths. | CI scripts and staging smoke suite |

## 3. Unit Tests

Test without Spring where possible:

- account balance formula and initial-balance changes;
- Monthly Savings and category aggregation;
- monthly boundary calculation in multiple timezones;
- budget spent/remaining/percentage/status and threshold transitions;
- transaction update/delete contribution reversal;
- notification read-state and idempotency;
- password policy and token hash utilities;
- JWT claim generation/validation;
- refresh rotation and reuse decision logic;
- application services with mocked ports for success and failure branches.

Use `BigDecimal` assertions that compare numeric value and intended scale where scale matters. Include zero, negative balance, four-decimal, boundary-date, warning-exactly-at-threshold, and over-limit cases.

## 4. Repository Tests

Use PostgreSQL Testcontainers rather than H2 for persistence behavior that depends on PostgreSQL types, partial indexes, case-insensitive text, constraints, triggers, or views.

Test:

- JPA mappings and UUID persistence;
- case-insensitive username/email/account/category uniqueness;
- owned-resource query scoping;
- transaction pagination, stable sorting, date ranges, search, and filters;
- monthly income/expense/category aggregates;
- duplicate monthly-budget constraint;
- unread-notification and refresh-token queries;
- soft-delete exclusion;
- cross-user and category-type database defenses;
- cached-balance trigger behavior and reconciliation view;
- Flyway migration from an empty database.

Each repository test starts from isolated known data and rolls back or recreates state. Avoid relying on test execution order.

## 5. Controller Tests

Use MockMvc with mocked application services to verify transport behavior:

- JSON parsing and standard response envelope;
- Bean Validation field errors;
- UUID/date/month/query parsing;
- pagination defaults and maximum size;
- bearer authentication requirement;
- role and status restrictions;
- `200`, `201`, `204`, `400`, `401`, `403`, `404`, `409`, and `422` mapping;
- stable error codes and no stack-trace leakage;
- absence of response body for `204`;
- sensitive fields never serialized.

Controller tests do not retest domain calculations.

## 6. Integration Tests

Run the real Spring context with PostgreSQL Testcontainers, Flyway, security filters, repositories, and transactional services.

Required flows:

1. Register, verify defaults, confirm email through a hashed single-use token, login, access protected profile.
2. Refresh token, verify rotation, reject reused predecessor.
3. Logout current token and logout all devices.
4. Change password and verify prior password/tokens fail.
5. Create account/category/expense and verify balance/dashboard.
6. Add income and verify Monthly Savings.
7. Edit transaction across account/category/month and reconcile old/new effects.
8. Delete transaction and verify effects reverse once.
9. Create monthly budget, reject duplicate, cross warning and exceeded thresholds once.
10. Update notification settings and suppress disabled alerts.
11. Mark one/all notifications read and verify timestamps/count.
12. Verify every owned-resource endpoint rejects another user.
13. Ban/delete user and verify login/refresh/protected access fail.
14. Run analytics over empty, normal, and bounded maximum ranges.
15. Detect an intentionally corrupted cached balance through reconciliation.

## 7. Security Tests

- JWT algorithm allow-list, signature, issuer, audience, expiry, malformed tokens.
- Missing bearer token and unsupported authentication scheme.
- BCrypt verification and no password/hash serialization.
- Refresh expiration, revocation, rotation, family reuse, and concurrent refresh race.
- Cross-user UUID enumeration for accounts, categories, transactions, budgets, notifications, and settings.
- Self-registration cannot assign `ADMIN`.
- Mass-assignment attempts cannot set owner IDs, source, balance, audit fields, or token state.
- Rate-limit behavior for login and refresh.
- Verification resend enumeration resistance/rate limits and invalid, expired, revoked, reused, or email-mismatched tokens.
- CORS allow-list and preflight behavior.
- Logs and error responses do not contain raw credentials or internal SQL details.

## 8. Migration Tests

For every Flyway change:

1. start an empty PostgreSQL container;
2. apply all migrations;
3. validate expected tables, constraints, indexes, views, and seed categories;
4. run repository smoke tests;
5. test upgrade from the previous released schema snapshot;
6. verify applied migrations were not edited.

Destructive changes require backup/restore rehearsal and a roll-forward repair migration.

## 9. Architecture and Contract Tests

- ArchUnit verifies feature-layer dependency rules from `architecture.md`.
- Controllers cannot depend directly on repositories.
- Domain packages cannot depend on API or infrastructure.
- Feature infrastructure is not imported by another feature.
- OpenAPI contains every documented endpoint and response.
- Contract diff fails CI on unapproved breaking changes.
- JSON examples are validated against generated schemas where tooling permits.

## 10. React Native Tests

- Unit-test DTO parsing, decimal handling, token refresh coordination, formatters, and API adapters with Jest.
- Component-test forms, validation, loading, empty, error, and success states with React Native Testing Library.
- Navigation tests cover authentication guards, responsive navigation, and logout on web, iOS, and Android targets.
- Secure-storage adapter tests verify native tokens use Expo SecureStore and browser storage is isolated behind the web adapter.
- Mock Service Worker or equivalent request mocks cover response envelopes, token refresh, validation errors, and unavailable APIs.
- Maestro or Detox integration tests exercise login, transaction entry, dashboard, budget, and settings against staging or a controlled local backend.

## 11. Test Naming and Structure

Java test method convention:

```text
methodName_condition_expectedResult
```

Examples:

- `calculateBalance_withIncomeAndExpense_returnsDerivedBalance`
- `createBudget_duplicateUserCategoryMonth_throwsConflict`
- `refreshToken_reusedRotatedToken_revokesFamily`

Use Arrange–Act–Assert structure. Test names express behavior, not implementation details.

## 12. Test Data

- Use builders or object mothers with safe defaults and explicit overrides.
- Generate UUIDs deterministically when assertions need stable values.
- Use fixed `Clock` and explicit `ZoneId` for temporal logic.
- Provide fixtures for two users to make ownership tests routine.
- Never use production exports or real personal data.
- Keep SQL seed data minimal; create scenario-specific state in tests.
- Reset external state and database containers between suites as needed.

## 13. Coverage Goals

Coverage is a signal, not the release objective:

| Area | Goal |
|---|---:|
| Domain calculations and security/token logic | At least 90% branch coverage |
| Application services | At least 85% line coverage |
| Overall backend | At least 75% line coverage |
| Controllers/configuration/generated mapping | Covered behaviorally; no arbitrary target |

No release may bypass missing tests for ownership, financial calculations, migrations, or token rotation merely because aggregate coverage passes.

## 14. CI Test Order

1. Compile and static analysis.
2. Fast unit tests.
3. Architecture and controller slice tests.
4. Repository and migration tests with PostgreSQL.
5. Full integration and security tests.
6. OpenAPI compatibility check.
7. Package JAR and build container.
8. Container and dependency scan.
9. Deploy to staging and run smoke tests.

Failed tests block image publication or deployment.

## 15. Release Acceptance

- All P0 requirements have automated success and failure coverage.
- All 15 documented use cases have at least one integration or system test.
- No known critical/high security finding remains unaccepted.
- Flyway succeeds from empty and prior-release schemas.
- Balance, Monthly Savings, budget, and cached-balance reconciliation tests pass.
- Cross-user access suite passes for every owned resource.
- Staging smoke tests pass after deployment.
