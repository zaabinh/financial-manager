# Business Rules

## 1. Purpose

This document defines domain rules for the Personal Finance Manager MVP. Rules apply regardless of client and must be enforced primarily by application services, with database constraints providing defense in depth.

## 2. Users and Settings

| ID | Rule |
|---|---|
| BR-USER-001 | Username is required, trimmed, compared case-insensitively, and unique among nondeleted users. |
| BR-USER-002 | Email is optional; when present it is normalized, valid, and unique among nondeleted users. |
| BR-USER-003 | Display name is required and must contain visible characters. |
| BR-USER-004 | User role is `USER` or `ADMIN`; self-registration always creates `USER`. |
| BR-USER-005 | User status is `ACTIVE`, `BANNED`, or `DELETED`. Only active users may authenticate or access protected APIs. |
| BR-USER-006 | Deleting a user is logical: set status to `DELETED`, set `deletedAt`, revoke refresh tokens, and prevent future login. |
| BR-USER-007 | A user may read or mutate only resources they own, except application default categories and explicit admin operations. |
| BR-USER-008 | Default currency is an uppercase ISO-4217 code and defaults to `VND`. Changing it does not convert historical amounts. |
| BR-USER-009 | Timezone must be a valid IANA timezone and defaults to `Asia/Ho_Chi_Minh`. |
| BR-USER-010 | Password hashes, token hashes, and internal security metadata are never returned by an API. |

## 3. Authentication

| ID | Rule |
|---|---|
| BR-AUTH-001 | Passwords are never stored or logged in plaintext and are hashed with BCrypt cost 12. |
| BR-AUTH-002 | A password contains at least 12 characters, does not exceed BCrypt's 72-byte UTF-8 input limit, and includes uppercase, lowercase, numeric, and special characters. |
| BR-AUTH-003 | Authentication failure messages do not reveal whether a username exists. |
| BR-AUTH-004 | Access tokens are HMAC-SHA-256 JWTs with a 15-minute lifetime and are not persisted. |
| BR-AUTH-005 | Access-token claims are limited to subject UUID, role, token ID, issuer, audience, issued time, and expiration. |
| BR-AUTH-006 | Refresh tokens are opaque random values with a seven-day lifetime; only SHA-256 hashes are stored. |
| BR-AUTH-007 | A refresh operation rotates the token: the presented token is revoked and linked to its replacement in the same token family. |
| BR-AUTH-008 | Reuse of a rotated refresh token revokes every active token in that token family and records a security event. |
| BR-AUTH-009 | Logout revokes the current refresh token. Logout-all and password changes revoke all active refresh tokens for the user. |
| BR-AUTH-010 | Expired, revoked, unknown, reused, or deleted-user refresh tokens cannot issue access tokens. |
| BR-AUTH-011 | Raw refresh tokens are returned only at issuance and are never written to application logs. |

## 4. Accounts

| ID | Rule |
|---|---|
| BR-ACCOUNT-001 | Account name is required, trimmed, and unique case-insensitively among the user's active, nondeleted accounts. |
| BR-ACCOUNT-002 | Account type is exactly `CASH`, `BANK`, or `SAVINGS`. |
| BR-ACCOUNT-003 | Account currency is a valid uppercase ISO-4217 code. The MVP performs no currency conversion. |
| BR-ACCOUNT-004 | Initial balance is decimal and may be positive, zero, or negative. |
| BR-ACCOUNT-005 | Current account balance equals initial balance plus nondeleted income minus nondeleted expenses. |
| BR-ACCOUNT-006 | Any physical `current_balance` column is a derived cache maintained atomically and is never accepted from API input or treated as the source of truth. |
| BR-ACCOUNT-007 | Changing initial balance changes calculated balance by the same delta and does not create or rewrite transactions. |
| BR-ACCOUNT-008 | The account referenced by a transaction must belong to the transaction owner. |
| BR-ACCOUNT-009 | Inactive or deleted accounts cannot receive new transactions. Historical transactions remain readable. |
| BR-ACCOUNT-010 | An account with transaction history is deactivated or soft-deleted, never physically deleted. |
| BR-ACCOUNT-011 | Total Balance includes only active, nondeleted accounts where `includeInTotal=true`; currencies are not converted or combined across differing currencies without grouping. |

## 5. Categories

| ID | Rule |
|---|---|
| BR-CATEGORY-001 | Category name is required and trimmed. |
| BR-CATEGORY-002 | Category transaction type is `INCOME` or `EXPENSE`. |
| BR-CATEGORY-003 | An application default category has no user owner and has `isDefault=true`. |
| BR-CATEGORY-004 | A user-created category belongs to exactly one user and has `isDefault=false`. |
| BR-CATEGORY-005 | Category names are unique case-insensitively for the same owner and transaction type among nondeleted categories. |
| BR-CATEGORY-006 | Ordinary users cannot edit, deactivate, or delete default categories. |
| BR-CATEGORY-007 | Users cannot view or use categories owned by another user. |
| BR-CATEGORY-008 | Inactive or deleted categories cannot be assigned to new transactions or budgets. |
| BR-CATEGORY-009 | A transaction's category type must equal the transaction type. |
| BR-CATEGORY-010 | A budget category must be an `EXPENSE` category. |
| BR-CATEGORY-011 | Categories with history are deactivated or soft-deleted so historical labels remain resolvable. |

## 6. Transactions

| ID | Rule |
|---|---|
| BR-TXN-001 | Transaction amount uses decimal arithmetic, supports at most four fractional digits, and is greater than zero. |
| BR-TXN-002 | Transaction type is `INCOME` or `EXPENSE`; direction is never represented by a negative amount. |
| BR-TXN-003 | Transaction date is required and interpreted in the user's timezone. |
| BR-TXN-004 | Transaction time is optional in API input. When omitted, the service resolves the current local time before persistence. |
| BR-TXN-005 | A transaction references exactly one valid account and one compatible category. |
| BR-TXN-006 | Account, user category, and transaction must share the same owner. A default category may be used by any active user. |
| BR-TXN-007 | MVP transaction source is always `MANUAL`. Reserved schema values `IMPORT`, `RECURRING`, and `SYSTEM` are not accepted by MVP APIs. |
| BR-TXN-008 | Description and note are optional plain text and must be normalized and length-limited by API validation. |
| BR-TXN-009 | Users cannot read, update, or delete another user's transaction; unowned UUIDs are reported as not found. |
| BR-TXN-010 | Updating a transaction reverses its prior financial contribution and applies the new contribution in one database transaction. |
| BR-TXN-011 | Deleting a transaction is logical and reverses its balance, dashboard, analytics, and budget contribution exactly once. |
| BR-TXN-012 | Nondeleted income adds to balance; nondeleted expense subtracts from balance. |
| BR-TXN-013 | Search and analytics ignore logically deleted transactions. |

## 7. Budgets

| ID | Rule |
|---|---|
| BR-BUDGET-001 | MVP budgets apply only to expense categories. |
| BR-BUDGET-002 | Each budget belongs to one user, one category, and one calendar month in that user's timezone. |
| BR-BUDGET-003 | Only one active, nondeleted budget may exist for a user/category/month combination. |
| BR-BUDGET-004 | Limit amount is decimal, supports at most four fractional digits, and is greater than zero. |
| BR-BUDGET-005 | Warning percentage is greater than or equal to 1 and less than or equal to 100; the default is 80. |
| BR-BUDGET-006 | Spent amount is the sum of nondeleted expense transactions in the category whose transaction date falls within the budget month. |
| BR-BUDGET-007 | Remaining Budget equals limit amount minus spent amount and may be negative. |
| BR-BUDGET-008 | Usage percentage equals spent amount divided by limit amount multiplied by 100. |
| BR-BUDGET-009 | Status is `SAFE` below warning percentage, `WARNING` at or above warning percentage but below 100%, and `EXCEEDED` at or above 100%. |
| BR-BUDGET-010 | At most one `BUDGET_WARNING` and one `BUDGET_EXCEEDED` notification are generated for a budget threshold cycle. |
| BR-BUDGET-011 | Editing or deleting transactions recalculates budget usage. A later upward crossing may notify again only after a documented threshold-state reset. |
| BR-BUDGET-012 | Period and rollover columns are reserved for future migrations; the MVP database and APIs enforce `MONTHLY` and `rolloverEnabled=false`. |
| BR-BUDGET-013 | Deactivated or deleted budgets do not generate notifications. |

## 8. Dashboard and Analytics

| ID | Rule |
|---|---|
| BR-DASH-001 | Dashboard values use only the authenticated user's accessible, nondeleted data. |
| BR-DASH-002 | Total Balance is distinct from Monthly Savings and Remaining Budget. |
| BR-DASH-003 | Current-month boundaries use the user's timezone, while stored timestamps remain UTC. |
| BR-DASH-004 | Monthly income is the sum of income transactions in the month. |
| BR-DASH-005 | Monthly expense is the sum of expense transactions in the month. |
| BR-DASH-006 | Monthly Savings equals monthly income minus monthly expense. |
| BR-DASH-007 | Recent transactions are ordered by transaction date, transaction time, and creation time descending. |
| BR-ANALYTICS-001 | Category-expense analytics include expense transactions only and group default and user categories by category UUID. |
| BR-ANALYTICS-002 | Income-versus-expense analytics return zero for a period with no matching transactions. |
| BR-ANALYTICS-003 | Analytics date ranges are inclusive, valid, and subject to a bounded maximum to protect performance. |
| BR-ANALYTICS-004 | Analytics never combine different currencies into one numeric total unless the response groups totals by currency. |

## 9. Notifications

| ID | Rule |
|---|---|
| BR-NOTIFICATION-001 | Each user has exactly one notification-settings record created at registration. |
| BR-NOTIFICATION-002 | Daily reminders use the user's IANA timezone and configured local reminder time. |
| BR-NOTIFICATION-003 | An enabled daily reminder generates at most one in-app notification per user/local calendar date. |
| BR-NOTIFICATION-004 | Disabled notification types do not generate user alerts. |
| BR-NOTIFICATION-005 | MVP delivery is in-app only; email, push, and quiet-hours fields are reserved and inactive. |
| BR-NOTIFICATION-006 | Notification type is `DAILY_REMINDER`, `BUDGET_WARNING`, `BUDGET_EXCEEDED`, or `SYSTEM`. |
| BR-NOTIFICATION-007 | An unread notification has `isRead=false` and `readAt=null`. |
| BR-NOTIFICATION-008 | A read notification has `isRead=true` and a nonnull UTC `readAt`. |
| BR-NOTIFICATION-009 | Marking an already-read notification as read is idempotent and preserves the first read timestamp. |
| BR-NOTIFICATION-010 | Users can list, read, and delete only their own notifications. |
| BR-NOTIFICATION-011 | Deleting a notification does not delete or modify its related budget or transaction. |

## 10. Data Lifecycle

| Entity | Strategy | Rule |
|---|---|---|
| User | Logical deletion | Preserve referential integrity and block access. |
| Account | Deactivate/soft delete | Preserve transaction history; physical deletion is allowed only before history exists. |
| Category | Deactivate/soft delete | Default categories are application-managed; user categories with history remain resolvable. |
| Transaction | Soft delete | Preserve auditability and reverse derived effects. |
| Budget | Soft delete | Preserve historical budget analysis and notification links. |
| Notification | Physical delete | User-facing message may be removed without changing related records. |
| Notification settings | Cascade with user | Exactly one record exists while the user exists. |
| Refresh token | Revoke then purge | Retain short-term security metadata; periodically purge expired/revoked records. |
| Subscription | Future retention policy | Reserved outside MVP and not part of current behavior. |

## 11. Enforcement Boundaries

- PostgreSQL enforces types, nullability, uniqueness, foreign keys, value ranges, read-state consistency, and selected cross-table ownership checks.
- Application services enforce authenticated ownership, active-state rules, monthly-only budget behavior, dormant-feature restrictions, token workflows, notification idempotency, and user-timezone semantics.
- Controllers perform transport parsing and Bean Validation but contain no business decisions.
- Both service and database safeguards apply to critical ownership and financial integrity rules.
