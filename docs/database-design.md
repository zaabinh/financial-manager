# Database Design

## 1. Database Choice

PostgreSQL 16 is the target database. It provides transactional integrity, mature indexing, reliable decimal and temporal types, partial indexes, JSON support for future extensions, and strong constraint capabilities. These features fit financial records where ownership, exact arithmetic, and atomic updates are more important than schemaless flexibility.

Flyway owns schema evolution. Hibernate validates mappings in production and must never generate or mutate the production schema.

## 2. Design Conventions

| Area | Convention |
|---|---|
| Tables | Lowercase plural `snake_case`, such as `refresh_tokens`. |
| Columns | Lowercase `snake_case`. |
| Primary keys | `id uuid PRIMARY KEY DEFAULT gen_random_uuid()`. |
| Foreign keys | `<entity>_id`, such as `account_id`. |
| Money | `numeric(19,4)`; Java uses `BigDecimal`. |
| Dates | `date` for user-calendar dates. |
| Times | `time` for user-local wall-clock settings. |
| Timestamps | `timestamptz`, stored and exchanged as UTC; names end in `_at`. |
| Booleans | Begin with `is_` or end with `_enabled` where practical. |
| Currency | Uppercase ISO-4217 `char(3)`; default `VND`. |
| Timezone | IANA identifier; default `Asia/Ho_Chi_Minh`. |
| Deletion | `deleted_at` for soft deletion; `is_active` for operational availability. |
| Enums | Constrained uppercase strings to keep SQL readable and Java mappings explicit. |

Public APIs and persistence use UUID identifiers. Numeric IDs shown in generic examples are not part of this design.

## 3. Relationship Model

```text
User 1 ── N Account
User 1 ── N Category (user categories only)
User 1 ── N Transaction
User 1 ── N Budget
User 1 ── N Notification
User 1 ── N RefreshToken
User 1 ── 1 NotificationSetting
Account 1 ── N Transaction
Category 1 ── N Transaction
Category 1 ── N Budget

Future:
User 1 ── N Subscription
SubscriptionPlan 1 ── N Subscription
```

Default categories have no user owner and are available to every user. Every other user-facing relationship is ownership-scoped.

## 4. MVP Tables

### 4.1 `users`

Purpose: authentication identity, profile, status, and user-level preferences.

| Column | Type | Required | Default | Constraints / Meaning |
|---|---|---:|---|---|
| `id` | uuid | Yes | Generated | Primary key. |
| `username` | citext | Yes | — | Trimmed, nonblank, unique among nondeleted users. |
| `email` | citext | No | null | Valid email; unique among nondeleted users when present. |
| `email_verified` | boolean | Yes | false | True only after successful confirmation; rollout migration grandfathers pre-verification users with email. |
| `password_hash` | varchar(255) | Yes | — | BCrypt hash only. |
| `display_name` | varchar(150) | Yes | — | Nonblank profile name. |
| `role` | varchar(20) | Yes | `USER` | `USER` or `ADMIN`. |
| `status` | varchar(20) | Yes | `ACTIVE` | `ACTIVE`, `BANNED`, or `DELETED`. |
| `currency` | char(3) | Yes | `VND` | Uppercase ISO-4217 code. |
| `timezone` | varchar(100) | Yes | `Asia/Ho_Chi_Minh` | Valid IANA timezone. |
| `created_at` | timestamptz | Yes | Current timestamp | Audit timestamp. |
| `updated_at` | timestamptz | Yes | Current timestamp | Updated automatically. |
| `deleted_at` | timestamptz | No | null | Required when status is `DELETED`. |

Indexes:

- Partial unique index on `lower(username)` where `deleted_at IS NULL`.
- Partial unique index on `lower(email)` where email is nonnull and `deleted_at IS NULL`.
- Index on `status` for operational and administrative queries.

### 4.2 `accounts`

Purpose: user-owned cash, bank, and savings containers.

| Column | Type | Required | Default | Constraints / Meaning |
|---|---|---:|---|---|
| `id` | uuid | Yes | Generated | Primary key. |
| `user_id` | uuid | Yes | — | FK to `users.id`; owner. |
| `name` | varchar(120) | Yes | — | Trimmed and nonblank. |
| `type` | varchar(20) | Yes | — | `CASH`, `BANK`, or `SAVING`. |
| `initial_balance` | numeric(19,4) | Yes | 0 | Starting balance; may be negative. |
| `current_balance` | numeric(19,4) | Yes | 0 | Optional physical cache maintained by triggers; never API-writable or authoritative. |
| `currency` | char(3) | Yes | `VND` | Account currency; no MVP conversion. |
| `is_active` | boolean | Yes | true | Inactive accounts reject new transactions. |
| `include_in_total` | boolean | Yes | true | Controls inclusion in Total Balance. |
| `display_order` | integer | Yes | 0 | Nonnegative user ordering. |
| `created_at` | timestamptz | Yes | Current timestamp | Audit timestamp. |
| `updated_at` | timestamptz | Yes | Current timestamp | Updated automatically. |
| `deleted_at` | timestamptz | No | null | Soft deletion marker. |

Constraints and indexes:

- FK `user_id → users.id`.
- Partial unique index on `(user_id, lower(name))` for nondeleted accounts.
- Index on `(user_id, is_active, display_order)` where not deleted.
- The database check constraint uses the documented `SAVING` value.

### 4.3 `categories`

Purpose: classify transactions and budgets as income or expense.

| Column | Type | Required | Default | Constraints / Meaning |
|---|---|---:|---|---|
| `id` | uuid | Yes | Generated | Primary key. |
| `user_id` | uuid | No | null | FK to owner; null only for default categories. |
| `name` | varchar(100) | Yes | — | Trimmed and nonblank. |
| `transaction_type` | varchar(20) | Yes | — | `INCOME` or `EXPENSE`. |
| `icon` | varchar(100) | No | null | Client-recognized icon key. |
| `color` | varchar(20) | No | null | Display color token or hex value. |
| `is_default` | boolean | Yes | false | True only when `user_id` is null. |
| `is_active` | boolean | Yes | true | Inactive categories reject new usage. |
| `display_order` | integer | Yes | 0 | Nonnegative display ordering. |
| `created_at` | timestamptz | Yes | Current timestamp | Audit timestamp. |
| `updated_at` | timestamptz | Yes | Current timestamp | Updated automatically. |
| `deleted_at` | timestamptz | No | null | Soft deletion marker. |

Indexes:

- Unique `(lower(name), transaction_type)` for active default categories.
- Unique `(user_id, lower(name), transaction_type)` for active user categories.
- Availability index on `(user_id, transaction_type, is_active)`.

### 4.4 `transactions`

Purpose: immutable-in-concept financial events that may be corrected through controlled updates or soft deletion.

| Column | Type | Required | Default | Constraints / Meaning |
|---|---|---:|---|---|
| `id` | uuid | Yes | Generated | Primary key. |
| `user_id` | uuid | Yes | — | FK to owner. |
| `account_id` | uuid | Yes | — | FK to owned account. |
| `category_id` | uuid | Yes | — | FK to compatible category. |
| `amount` | numeric(19,4) | Yes | — | Greater than zero; direction comes from type. |
| `transaction_date` | date | Yes | User-local current date | Required user-calendar date. |
| `transaction_time` | time | Yes after normalization | User-local current time | API may omit; service resolves before insert. |
| `transaction_type` | varchar(20) | Yes | — | `INCOME` or `EXPENSE`. |
| `description` | text | No | null | Short searchable description. |
| `note` | text | No | null | Optional detail. |
| `source` | varchar(30) | Yes | `MANUAL` | MVP accepts only `MANUAL`. |
| `created_at` | timestamptz | Yes | Current timestamp | Audit timestamp. |
| `updated_at` | timestamptz | Yes | Current timestamp | Updated automatically. |
| `deleted_at` | timestamptz | No | null | Soft deletion marker. |

Indexes:

- `(user_id, transaction_date DESC, transaction_time DESC)` for lists and reports.
- `(account_id, transaction_date DESC)` for account history.
- `(category_id, transaction_date DESC)` for category analytics.
- `(user_id, transaction_type, transaction_date DESC)` for monthly aggregation.

Database triggers may validate account ownership, category ownership/type, active state, and cached-balance maintenance. Service checks remain mandatory to produce stable API errors.

### 4.5 `budgets`

Purpose: monthly expense limits by category.

| Column | Type | Required | Default | Constraints / Meaning |
|---|---|---:|---|---|
| `id` | uuid | Yes | Generated | Primary key. |
| `user_id` | uuid | Yes | — | FK to owner. |
| `category_id` | uuid | Yes | — | FK to active expense category. |
| `name` | varchar(120) | No | null | Optional display label. |
| `period_type` | varchar(20) | Yes | `MONTHLY` | MVP always `MONTHLY`. |
| `start_date` | date | Yes | — | First day of calendar month. |
| `end_date` | date | Yes | — | Last day of the same calendar month. |
| `limit_amount` | numeric(19,4) | Yes | — | Greater than zero. |
| `warning_percentage` | numeric(5,2) | Yes | 80 | From 1 through 100. |
| `rollover_enabled` | boolean | Yes | false | Reserved; must remain false in MVP. |
| `is_active` | boolean | Yes | true | Controls usage and alert generation. |
| `created_at` | timestamptz | Yes | Current timestamp | Audit timestamp. |
| `updated_at` | timestamptz | Yes | Current timestamp | Updated automatically. |
| `deleted_at` | timestamptz | No | null | Soft deletion marker. |

Constraints and indexes:

- Unique partial index on `(user_id, category_id, start_date, end_date)` where not deleted.
- Index on `(user_id, is_active, start_date, end_date)`.
- Database checks require a complete calendar month, `period_type='MONTHLY'`, `rollover_enabled=false`, a positive limit, and a valid warning percentage.
- Service validation applies the same monthly restrictions before persistence.

### 4.6 `notification_settings`

Purpose: one notification preference record per user.

| Column | Type | Required | Default | Constraints / Meaning |
|---|---|---:|---|---|
| `id` | uuid | Yes | Generated | Primary key. |
| `user_id` | uuid | Yes | — | Unique FK to `users.id`. |
| `daily_reminder_enabled` | boolean | Yes | true | Enables daily in-app reminder. |
| `daily_reminder_time` | time | Yes | 20:00 | Interpreted in user timezone. |
| `budget_warning_enabled` | boolean | Yes | true | Enables threshold warning. |
| `budget_exceeded_enabled` | boolean | Yes | true | Enables limit-exceeded alert. |
| `system_notifications_enabled` | boolean | Yes | true | Enables essential in-app system notices. |
| `in_app_enabled` | boolean | Yes | true | MVP delivery channel. |
| `email_enabled` | boolean | Yes | false | Reserved; inactive in MVP. |
| `push_enabled` | boolean | Yes | false | Reserved; inactive in MVP. |
| `quiet_hours_enabled` | boolean | Yes | false | Reserved; inactive in MVP. |
| `quiet_hours_start` | time | No | null | Future capability. |
| `quiet_hours_end` | time | No | null | Future capability. |
| `created_at` | timestamptz | Yes | Current timestamp | Audit timestamp. |
| `updated_at` | timestamptz | Yes | Current timestamp | Updated automatically. |

A unique index on `user_id` enforces one-to-one cardinality. Registration creates this row in the same transaction as the user.

### 4.7 `notifications`

Purpose: user-owned in-app messages.

| Column | Type | Required | Default | Constraints / Meaning |
|---|---|---:|---|---|
| `id` | uuid | Yes | Generated | Primary key. |
| `user_id` | uuid | Yes | — | FK to owner. |
| `title` | varchar(200) | Yes | — | Nonblank. |
| `content` | text | Yes | — | Nonblank message content. |
| `notification_type` | varchar(30) | Yes | — | Reminder, budget warning/exceeded, or system. |
| `related_entity_type` | varchar(30) | No | null | Optional domain reference type. |
| `related_entity_id` | uuid | No | null | Optional domain UUID; intentionally not a polymorphic FK. |
| `is_read` | boolean | Yes | false | Read state. |
| `created_at` | timestamptz | Yes | Current timestamp | Creation time. |
| `read_at` | timestamptz | No | null | Required exactly when read. |

Indexes:

- Partial index on `(user_id, created_at DESC)` where `is_read=false`.
- Index on `(user_id, created_at DESC)` for notification lists.
- Notification generation uses application idempotency to prevent duplicate daily and threshold alerts.

### 4.8 `refresh_tokens`

Purpose: server-side state for opaque refresh-token rotation and revocation.

| Column | Type | Required | Default | Constraints / Meaning |
|---|---|---:|---|---|
| `id` | uuid | Yes | Generated | Primary key. |
| `user_id` | uuid | Yes | — | FK to user. |
| `token_hash` | varchar(255) | Yes | — | Unique SHA-256 hash; raw token is never stored. |
| `token_family_id` | uuid | Yes | Generated | Groups rotated tokens for reuse response. |
| `expires_at` | timestamptz | Yes | — | Seven days after issuance. |
| `created_at` | timestamptz | Yes | Current timestamp | Issuance time. |
| `last_used_at` | timestamptz | No | null | Last successful presentation. |
| `revoked_at` | timestamptz | No | null | Revocation time. |
| `replaced_by_token_id` | uuid | No | null | Self-FK to replacement. |
| `revoke_reason` | varchar(255) | No | null | Sanitized machine-readable reason. |
| `user_agent` | text | No | null | Sanitized device metadata. |
| `ip_address` | inet | No | null | Security metadata subject to retention policy. |

Indexes:

- Unique index on `token_hash`.
- Partial index on `(user_id, expires_at)` where not revoked.
- Index on `token_family_id` for reuse response.
- Index on `expires_at` for purge jobs.

### 4.9 `email_verification_tokens`

Purpose: single-use verification state for the user's current email address.

| Column | Type | Required | Default | Constraints / Meaning |
|---|---|---:|---|---|
| `id` | uuid | Yes | Generated | Primary key. |
| `user_id` | uuid | Yes | — | FK to user with cascade delete. |
| `email` | citext | Yes | — | Email snapshot that must match the user's current email at confirmation. |
| `token_hash` | char(64) | Yes | — | Unique lowercase SHA-256 hash; raw token is never stored. |
| `expires_at` | timestamptz | Yes | — | 24 hours after issuance. |
| `created_at` | timestamptz | Yes | Current timestamp | Issuance time. |
| `used_at` | timestamptz | No | null | Successful confirmation time. |
| `revoked_at` | timestamptz | No | null | Replacement or invalidation time. |

Indexes:

- Unique index on `token_hash`.
- Partial index on `(user_id, expires_at)` where unused and unrevoked.
- Index on `expires_at` for purge jobs.

## 5. Future-Reserved Subscription Tables

Subscriptions are outside the MVP and have no public API or use case. The draft schema may retain these tables for future planning, but MVP services must not depend on them.

### 5.1 `subscription_plans`

Potential future plan catalog containing code, name, price, currency, billing period, account/budget limits, feature metadata, active state, and audit timestamps.

### 5.2 `subscriptions`

Potential future user plan history containing user, plan, status, validity period, cancellation state, external-provider references, and audit timestamps. Payment processing and external provider integration require a separate approved design before activation.

## 6. Derived Financial Values

### 6.1 Current Account Balance

```text
Current account balance =
initial balance
+ total nondeleted income
- total nondeleted expense
```

The transaction ledger is authoritative. The API never accepts `currentBalance` in create or update requests. The draft SQL cache may be retained for query performance only when:

- triggers update it atomically for transaction insert, update, soft delete, restore, and account transfer;
- initial-balance changes apply only their delta;
- a reconciliation query compares cached and calculated values;
- discrepancies produce an operational alert and can be repaired from the ledger.

### 6.2 Monthly Savings

```text
Monthly Savings = monthly income - monthly expense
```

It is calculated for a user-local calendar month and is unrelated to Total Balance or Remaining Budget.

### 6.3 Budget Usage

```text
spent amount = sum of matching monthly expense transactions
remaining amount = limit amount - spent amount
usage percentage = spent amount / limit amount × 100
```

## 7. Soft Delete and Physical Delete

| Entity | Policy |
|---|---|
| Users | Logical deletion through status and `deleted_at`. |
| Accounts | Deactivate or soft-delete when history exists; physical deletion only before history. |
| Categories | Default categories are retained; user categories with history are deactivated or soft-deleted. |
| Transactions | Soft-delete and reverse derived effects. |
| Budgets | Soft-delete to preserve historical context. |
| Notifications | Physical deletion is acceptable. |
| Notification settings | Cascade physical deletion with the user record only under an approved retention process. |
| Refresh tokens | Revoke immediately; purge expired/revoked rows after the security retention window. |
| Email verification tokens | Revoke on replacement/email change; cascade with user; purge expired, used, and revoked rows after the security retention window. |
| Future subscriptions | Retain as auditable history under a future policy. |

## 8. Integrity Enforcement Matrix

| Rule | PostgreSQL | Service Layer |
|---|---:|---:|
| Required fields, types, positive amounts | Yes | Yes, for stable errors |
| Username/email uniqueness | Yes | Yes, precheck plus conflict handling |
| Owned account/category relationship | Trigger/FK defense | Yes, authoritative authorization |
| Category and transaction type match | Trigger defense | Yes |
| Expense-only budget category | Trigger defense | Yes |
| Monthly-only budget behavior | Generic date constraints only | Yes |
| Duplicate monthly budget | Unique date-range index | Yes |
| Read timestamp consistency | Check/trigger | Yes |
| Refresh-token rotation/reuse | Structural constraints | Yes |
| Email verification token safety | Hash/expiry/time/FK constraints | Yes, authoritative lifecycle and email match |
| Dormant source/channel restrictions | Enum may be broader | Yes |
| Cached-balance reconciliation | Trigger/view | Yes, monitoring and repair |

## 9. Index Strategy

- Transaction list and reports: user plus descending transaction date/time.
- Account and category history: foreign key plus descending date.
- Budget lookup: user, active status, and period; unique user/category/month.
- Unread notifications: partial user/created index.
- Active refresh tokens: partial user/expiration index and token-family index.
- Active email verification tokens: partial user/expiration index and unique token hash.
- User and category names: case-insensitive partial unique indexes excluding soft-deleted rows.
- Query plans must be reviewed with production-like data before adding redundant indexes.

## 10. Data Integrity Risks

- **Cross-user references:** plain foreign keys prove existence but not common ownership. Service checks are mandatory; triggers provide defense in depth.
- **Category mismatch:** category type must match transaction type, and budgets require expense categories.
- **Default category ownership:** exactly one of default-without-owner or user-owned-not-default is valid.
- **Duplicate budgets:** month boundaries must be normalized before insert so equivalent months cannot use differing ranges.
- **Token exposure:** only hashes are persisted; raw token values must not enter SQL logs or traces.
- **Cached balance drift:** every ledger mutation path must be trigger-covered and monitored by reconciliation.
- **Multiple currencies:** no conversion exists; totals must remain currency-specific unless all included accounts share a currency.

## 11. Migration Strategy

1. Create one versioned Flyway migration per coherent change.
2. Never edit a migration applied to a shared environment.
3. Use repeatable migrations only for replaceable views or functions where appropriate.
4. Back up production before destructive or high-risk migrations.
5. Prefer expand-and-contract changes for zero-downtime compatibility.
6. Roll forward with a corrective migration; do not depend on automatic down migrations.
7. Validate migrations with Testcontainers PostgreSQL in CI.
8. Validate every migration against the documented `SAVINGS` enum, monthly constraints, and cached-balance reconciliation rules.
9. A database created manually from `db.sql` is adopted once at Flyway baseline version 9, reconciled by `V10`, aligned with the current user schema by `V11`, and upgraded with verification tokens by `V12`; baseline-on-migrate is disabled afterward.
