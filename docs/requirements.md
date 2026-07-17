# Software Requirements Specification

## 1. Project Overview

Personal Finance Manager is a mobile-first application for people who need a simple, private way to record income and expenses, organize money across cash, bank, and savings accounts, control monthly category spending, and understand their financial position.

The MVP consists of a Flutter mobile client and a Java 21/Spring Boot modular monolith backed by PostgreSQL. It is designed for manual personal-finance tracking. It does not move money, connect to financial institutions, or provide regulated financial advice.

### 1.1 Product Objectives

- Make recording an income or expense fast enough for daily use.
- Show total balance, monthly income, monthly expenses, and Monthly Savings without ambiguity.
- Help users control spending through monthly category budgets and in-app warnings.
- Keep every user's financial records private and isolated.
- Establish maintainable architecture and contracts that can evolve without premature microservices.

### 1.2 Terminology

| Term | Definition |
|---|---|
| Total Balance | Sum of current balances for active accounts included in totals. |
| Current Account Balance | Initial balance plus posted income minus posted expenses for one account. |
| Monthly Savings | Income in a calendar month minus expenses in the same month. |
| Remaining Budget | Monthly category limit minus qualifying expense transactions. |
| Default Category | Application-managed category available to every user and owned by no user. |
| User Category | Category created and owned by one user. |

## 2. Target Users

- Students tracking allowances, living costs, and savings.
- Office workers tracking salary and household spending.
- Freelancers tracking irregular income and expenses.
- Individuals who want lightweight finance tracking without bank synchronization.

## 3. MVP Scope

The MVP includes:

- Registration, login, logout, logout from all devices, refresh-token rotation, and password changes.
- User profile, currency, timezone, and application settings.
- Cash, bank, and savings account management.
- Default and user-created income and expense categories.
- Manual income and expense transactions with search, filtering, sorting, and pagination.
- Monthly expense-category budgets and usage status.
- Dashboard totals and monthly summary.
- Category expenses, income-versus-expense, and Monthly Savings analytics.
- Daily reminder preferences, budget warning preferences, and in-app notifications.

### 3.1 Out of Scope for MVP

- Bank synchronization or open-banking integrations.
- Stock-market, cryptocurrency, or investment portfolio tracking.
- Automatic currency conversion or exchange-rate services.
- Shared family, team, or joint accounts.
- Recurring transactions or automated transaction imports.
- Credit scoring, lending, or AI financial recommendations.
- Payment processing or subscription purchasing.
- Email and push notification delivery.

The draft schema contains reserved fields for some future capabilities. Their presence does not place those capabilities in MVP scope.

## 4. Assumptions and Constraints

- All public resource identifiers are UUIDs.
- Monetary values use decimal arithmetic with up to four fractional digits.
- The default currency is VND; each account has one currency and the MVP performs no conversion.
- The default timezone is `Asia/Ho_Chi_Minh`; users may choose another valid IANA timezone.
- Dates are interpreted in the user's timezone; persisted timestamps use UTC.
- Transactions are manual in the MVP.
- Budgets cover calendar months only.
- The mobile client stores tokens using platform secure storage.
- Subscriptions and subscription plans are reserved for a future release.

## 5. Functional Requirements

Priority definitions: **P0** is required for MVP release; **P1** is required for a complete MVP but may follow the first internal build.

### 5.1 Authentication

| ID | Title | Description | Priority | Acceptance Criteria |
|---|---|---|---|---|
| FR-AUTH-001 | Register | A visitor can create an account with username, password, display name, and optional email. | P0 | Valid data creates one active user and default notification settings; duplicate username or email returns `409`; password is never persisted or logged in plaintext. |
| FR-AUTH-002 | Login | An active user can authenticate with username and password. | P0 | Valid credentials return an access token, rotated-capable refresh token, expiry metadata, and user summary; invalid credentials return `401` without revealing which field failed. |
| FR-AUTH-003 | Refresh session | A client can exchange an active refresh token for a new token pair. | P0 | The old token is revoked, a replacement token is issued in the same family, and expired, revoked, or reused tokens return `401`. |
| FR-AUTH-004 | Logout | A user can end the current device session. | P0 | The submitted refresh token is revoked; repeating logout is safe; the access token naturally expires. |
| FR-AUTH-005 | Logout all | A user can end all refresh-token sessions. | P1 | Every active refresh token belonging to the user is revoked and can no longer refresh a session. |
| FR-AUTH-006 | Change password | An authenticated user can change their password after confirming the current password. | P0 | The new password meets policy, differs from the current password, is hashed with BCrypt, and all refresh tokens are revoked. |

### 5.2 Users and Settings

| ID | Title | Description | Priority | Acceptance Criteria |
|---|---|---|---|---|
| FR-USER-001 | View profile | An authenticated user can view their own profile and preferences. | P0 | The response contains no password hash, token hash, or internal security metadata. |
| FR-USER-002 | Update profile | A user can update display name and optional email. | P0 | Blank display names and conflicting emails are rejected; only the authenticated user's profile changes. |
| FR-USER-003 | Delete profile | A user can logically delete their profile. | P1 | Status becomes `DELETED`, all refresh tokens are revoked, login is blocked, and financial history is no longer accessible through the API. |
| FR-SET-001 | Update preferences | A user can update default currency and timezone. | P0 | Currency is a valid uppercase ISO-4217 code; timezone is a valid IANA identifier; changes affect future display and date interpretation but do not convert existing values. |
| FR-SET-002 | Preserve settings | Profile and application settings persist across sessions and devices. | P1 | A later authenticated request returns the latest saved values. |

### 5.3 Accounts

| ID | Title | Description | Priority | Acceptance Criteria |
|---|---|---|---|---|
| FR-ACC-001 | List accounts | A user can list their active and inactive accounts. | P0 | Only owned accounts are returned; status filtering and deterministic ordering are supported. |
| FR-ACC-002 | View account | A user can view one owned account and its calculated balance. | P0 | Another user's UUID is not disclosed and returns `404`; balance matches the transaction formula. |
| FR-ACC-003 | Create account | A user can create a `CASH`, `BANK`, or `SAVINGS` account. | P0 | Name is nonblank, currency is valid, initial balance is decimal, and active account names are unique per user case-insensitively. |
| FR-ACC-004 | Update account | A user can update account metadata and initial balance. | P0 | Ownership is enforced; changing initial balance updates the calculated balance by the same delta without rewriting transactions. |
| FR-ACC-005 | Deactivate account | A user can deactivate or reactivate an account. | P0 | Deactivated accounts remain queryable but cannot receive new transactions; accounts with history are not physically deleted. |
| FR-ACC-006 | Calculate account balance | The system calculates current balance from initial balance and transactions. | P0 | Income adds and expense subtracts; edits and deletions are reflected; any database cache reconciles to the derived value. |

### 5.4 Categories

| ID | Title | Description | Priority | Acceptance Criteria |
|---|---|---|---|---|
| FR-CAT-001 | List categories | A user can list applicable default and owned categories. | P0 | Results can be filtered by transaction type, default status, and active status; another user's categories are excluded. |
| FR-CAT-002 | Create category | A user can create an income or expense category. | P0 | Name is nonblank and unique per owner and type among nondeleted categories; the category is not marked default. |
| FR-CAT-003 | Update category | A user can update an owned category. | P0 | Default categories and categories owned by others cannot be changed; changing type is rejected when it would invalidate history. |
| FR-CAT-004 | Deactivate category | A user can deactivate or reactivate an owned category. | P0 | Inactive categories cannot be selected for new transactions or budgets; historical records remain readable. |
| FR-CAT-005 | Delete category | A user can delete an unused owned category. | P1 | Categories with transaction or budget history are deactivated or soft-deleted rather than physically removed. |

### 5.5 Transactions

| ID | Title | Description | Priority | Acceptance Criteria |
|---|---|---|---|---|
| FR-TXN-001 | Create transaction | A user can record a manual income or expense. | P0 | Amount is positive; date, owned active account, and compatible active category are required; balance, dashboard, analytics, and budget usage reflect the transaction. |
| FR-TXN-002 | List transactions | A user can page, sort, filter, and search their transactions. | P0 | Filters include date range, account, category, and type; description search is case-insensitive; no other user's data is returned. |
| FR-TXN-003 | View transaction | A user can view one owned transaction. | P0 | Full transaction detail is returned for the owner; unknown or unowned UUIDs return `404`. |
| FR-TXN-004 | Update transaction | A user can edit an owned transaction. | P0 | The same validations as create apply; old and new balance, analytics, and budget effects are correctly reconciled. |
| FR-TXN-005 | Delete transaction | A user can logically delete an owned transaction. | P0 | The transaction disappears from normal queries and its financial effects are reversed exactly once. |

### 5.6 Budgets

| ID | Title | Description | Priority | Acceptance Criteria |
|---|---|---|---|---|
| FR-BUD-001 | Create monthly budget | A user can set a positive monthly limit for an owned or default expense category. | P0 | One active budget exists per user/category/month; income categories and duplicate periods are rejected. |
| FR-BUD-002 | Manage budget | A user can list, view, update, and delete their budgets. | P0 | Ownership is enforced; updates preserve uniqueness and recalculate usage; deleted budgets stop generating alerts. |
| FR-BUD-003 | View budget usage | A user can view limit, spent amount, remaining amount, usage percentage, and status. | P0 | Only nondeleted expense transactions within the calendar month contribute; statuses are `SAFE`, `WARNING`, or `EXCEEDED`. |
| FR-BUD-004 | Trigger threshold warnings | The system generates in-app alerts when warning and limit thresholds are first crossed. | P1 | At most one warning and one exceeded notification are generated per budget threshold cycle; disabled alert types generate nothing. |

### 5.7 Dashboard and Analytics

| ID | Title | Description | Priority | Acceptance Criteria |
|---|---|---|---|---|
| FR-DASH-001 | View dashboard | A user can view total balance, current-month income, expense, Monthly Savings, budget summary, and recent transactions. | P0 | Values are based only on owned, nondeleted data; accounts excluded from totals do not affect total balance. |
| FR-DASH-002 | View monthly summary | A user can request summary values for a specified calendar month. | P0 | Month boundaries use the user's timezone and the response distinguishes income, expense, and Monthly Savings. |
| FR-ANA-001 | Category expenses | A user can view expense totals grouped by category for a date range. | P1 | Only expense transactions are included; totals and percentages match the filtered expense total. |
| FR-ANA-002 | Income versus expense | A user can compare income and expense totals by month. | P1 | Empty months return zero values and requested date ranges are bounded and validated. |
| FR-ANA-003 | Monthly savings trend | A user can view Monthly Savings by month. | P1 | Each point equals monthly income minus monthly expense and is ordered chronologically. |

### 5.8 Notifications

| ID | Title | Description | Priority | Acceptance Criteria |
|---|---|---|---|---|
| FR-NOT-001 | Configure notifications | A user can configure daily reminder time and budget warning/exceeded switches. | P0 | Reminder time is interpreted in the user's timezone; only owned settings are read or changed. |
| FR-NOT-002 | Daily reminder | The system can generate one in-app daily reminder when enabled. | P1 | The reminder follows the user's timezone and is not generated when disabled. |
| FR-NOT-003 | List notifications | A user can page their notifications and filter by read state. | P0 | Results are newest first and never include another user's notifications. |
| FR-NOT-004 | Read notifications | A user can mark one or all notifications as read. | P0 | Read notifications have `isRead=true` and a nonnull `readAt`; repeated requests are idempotent. |
| FR-NOT-005 | Unread count | A user can retrieve the number of unread notifications. | P0 | The count matches owned rows where `isRead=false`. |
| FR-NOT-006 | Delete notification | A user can remove an owned notification. | P1 | An existing owned notification is removed and related financial records are not affected; absent or unowned identifiers return `404`. |

## 6. Non-Functional Requirements

| ID | Area | Requirement | Acceptance Measure |
|---|---|---|---|
| NFR-SEC-001 | Authentication | Protected APIs require a valid signed access token. | Requests without a valid token return `401`; no protected data is returned. |
| NFR-SEC-002 | Authorization | Resource ownership and role checks occur in the service layer. | Automated tests cover cross-user access for every owned resource. |
| NFR-SEC-003 | Secrets | Passwords, raw refresh tokens, and production secrets are never committed or logged. | Secret scanning and log-review checks pass in CI. |
| NFR-SEC-004 | Transport | Staging and production traffic uses HTTPS. | Plain HTTP is redirected or rejected at the edge. |
| NFR-PERF-001 | API latency | Normal CRUD reads and writes should complete within 500 ms at p95 under expected MVP load. | Staging performance tests meet the target excluding network latency. |
| NFR-PERF-002 | Analytics latency | Dashboard and analytics requests should complete within 1 second at p95 for up to five years of personal data. | Indexed staging dataset tests meet the target. |
| NFR-REL-001 | Availability | Production target availability is 99.5% monthly after launch stabilization. | Monitoring reports monthly uptime excluding scheduled maintenance. |
| NFR-REL-002 | Idempotency | Token revocation and notification read operations tolerate retries. | Repeated identical requests do not corrupt state or duplicate effects. |
| NFR-MNT-001 | Maintainability | Features follow modular boundaries and automated tests. | Architecture checks prevent forbidden cross-module dependencies. |
| NFR-MNT-002 | API documentation | Public endpoints are represented in an OpenAPI contract. | CI verifies that the contract is generated and publishable. |
| NFR-USA-001 | Usability | Common transaction entry requires minimal mobile input. | A user can record an expense with account, category, amount, and date in one flow. |
| NFR-USA-002 | Error clarity | Validation errors identify affected fields without exposing internals. | API errors use stable codes and field-error entries. |
| NFR-SCL-001 | Scalability | Backend instances remain stateless except for PostgreSQL-backed state. | Multiple instances can serve requests behind a load balancer. |
| NFR-LOG-001 | Logging | Requests use structured logs and a correlation identifier. | Every API error can be traced without logging sensitive values. |
| NFR-LOG-002 | Auditability | Security-relevant events are recorded. | Login failures, password changes, token reuse, and account deletion have sanitized audit events. |
| NFR-DATA-001 | Integrity | Financial changes are atomic and use decimal arithmetic. | Transaction changes and their dependent effects commit or roll back together. |
| NFR-DATA-002 | Ownership | Database and service safeguards prevent cross-user relationships. | Invalid account/category/user combinations are rejected. |
| NFR-BACKUP-001 | Backup | Production PostgreSQL receives automated daily backups. | Restore tests demonstrate an RPO of 24 hours and RTO of 4 hours. |
| NFR-PRI-001 | Privacy | Users can access only their data and can request logical account deletion. | Authorization tests pass and deleted users cannot authenticate. |
| NFR-PRI-002 | Retention | Token and notification retention is bounded. | Expired/revoked tokens and old notifications can be purged by documented maintenance jobs. |

## 7. MVP Release Acceptance

The MVP is releasable when:

1. All P0 functional requirements pass automated acceptance tests.
2. P1 items selected for release are either complete or explicitly deferred in release notes.
3. Security, ownership, migration, backup, and rollback checks pass in staging.
4. Dashboard, balance, budget, and Monthly Savings calculations reconcile against transaction data.
5. The Flutter client completes register, login, account, transaction, dashboard, budget, notification, and settings flows.
6. API, database, security, operations, and user-facing terminology match this documentation set.
