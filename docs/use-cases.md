# Use Cases

## UC-001 Register

| Item | Detail |
|---|---|
| Primary actor | Visitor |
| Preconditions | No authenticated session is required. |
| Trigger | Visitor submits the registration form. |
| Main flow | 1. Client validates required fields. 2. API normalizes username/email. 3. System verifies uniqueness and password policy. 4. Password is BCrypt-hashed. 5. User and notification settings are created atomically. 6. When email is present, a hashed 24-hour token is stored and a verification link is sent. 7. User opens the link and the client confirms the token. 8. API marks the email verified and returns the user. |
| Alternative flow | Email may be omitted and no verification is required; currency/timezone defaults apply; a user may request a replacement link with a uniform `204` response. |
| Error flow | Invalid fields return `422`; existing username/email returns `409`; invalid/expired verification token returns `422`; resend rate limit returns `429`; mail failure is logged without exposing the token and the user can retry resend. |
| Postconditions | One active `USER` exists; when email was supplied it is verified before login; no session exists until login. |
| Related rules | BR-USER-001–005, BR-USER-008–010, BR-AUTH-001–002, BR-AUTH-012–016, BR-NOTIFICATION-001 |
| Related API | `POST /api/v1/auth/register`, `POST /api/v1/auth/email-verification/request`, `POST /api/v1/auth/email-verification/confirm` |

## UC-002 Login

| Item | Detail |
|---|---|
| Primary actor | Registered user |
| Preconditions | User exists with status `ACTIVE`; if email is present, it is verified. |
| Trigger | User submits username and password. |
| Main flow | 1. API applies rate limiting. 2. User is located by normalized username. 3. Password hash is verified. 4. JWT access token and opaque refresh token are issued. 5. Refresh-token hash and device metadata are persisted. 6. Token pair and user summary are returned. |
| Alternative flow | Optional device name is retained as sanitized metadata. |
| Error flow | Invalid credentials or inactive account returns indistinguishable `401`; a correct login with unverified email returns `403 EMAIL_NOT_VERIFIED`; excessive attempts return `429`. |
| Postconditions | Client stores both tokens in secure platform storage; an active refresh-token row exists. |
| Related rules | BR-USER-005, BR-AUTH-003–006, BR-AUTH-011 |
| Related API | `POST /api/v1/auth/login` |

## UC-003 Refresh Session

| Item | Detail |
|---|---|
| Primary actor | Authenticated mobile session |
| Preconditions | Client possesses an unexpired, unrevoked refresh token. |
| Trigger | Access token is near expiry or an authenticated request returns token-expired `401`. |
| Main flow | 1. Client submits refresh token. 2. API hashes and finds the token. 3. User and token state are validated. 4. Presented token is revoked. 5. New access and refresh tokens are issued in the same family. 6. Replacement link is persisted atomically. |
| Alternative flow | Client retries normal API request once after successful rotation. |
| Error flow | Expired/revoked token returns `401`; reuse revokes the family and records a security event; client clears credentials and returns to login. |
| Postconditions | Exactly one latest token in the family is usable. |
| Related rules | BR-AUTH-004–011 |
| Related API | `POST /api/v1/auth/refresh` |

## UC-004 Create Account

| Item | Detail |
|---|---|
| Primary actor | Authenticated user |
| Preconditions | User is active. |
| Trigger | User submits a new account form. |
| Main flow | 1. Client sends name, type, balance, currency, and display preferences. 2. API validates `CASH`, `BANK`, or `SAVINGS`. 3. Service checks owned-name uniqueness. 4. Account is created active. 5. Calculated balance starts at initial balance. 6. API returns `201`. |
| Alternative flow | Initial balance defaults to zero; currency defaults to user currency. |
| Error flow | Invalid values return `422`; duplicate active name returns `409`. |
| Postconditions | Account appears in account lists and, when included, Total Balance. |
| Related rules | BR-ACCOUNT-001–007, BR-ACCOUNT-011 |
| Related API | `POST /api/v1/accounts` |

## UC-005 Add Expense

| Item | Detail |
|---|---|
| Primary actor | Authenticated user |
| Preconditions | User has an active owned account and an active applicable expense category. |
| Trigger | User submits an expense entry. |
| Main flow | 1. API validates positive amount and date. 2. Service loads account and category in the user's scope. 3. Category type is confirmed as `EXPENSE`. 4. Missing time is resolved in the user timezone. 5. Manual transaction is persisted atomically. 6. Balance and budget effects are applied. 7. Threshold notifications are generated idempotently when enabled. 8. API returns `201`. |
| Alternative flow | Description, note, and time may be omitted; a default category may be used. |
| Error flow | Missing resources return `404`; inactive, cross-owner, amount, or type violations return `422`; transaction rolls back on dependent-effect failure. |
| Postconditions | Expense appears in history and reduces balance, Monthly Savings, and applicable Remaining Budget. |
| Related rules | BR-TXN-001–008, BR-TXN-012, BR-BUDGET-006–013 |
| Related API | `POST /api/v1/transactions` |

## UC-006 Add Income

| Item | Detail |
|---|---|
| Primary actor | Authenticated user |
| Preconditions | User has an active owned account and active applicable income category. |
| Trigger | User submits an income entry. |
| Main flow | 1. API validates amount/date. 2. Ownership and active states are checked. 3. Category type is confirmed as `INCOME`. 4. Time is normalized. 5. Manual transaction is stored. 6. Account balance and analytics reflect the income. 7. API returns `201`. |
| Alternative flow | Description, note, and time may be omitted. |
| Error flow | Inaccessible resources return `404`; type mismatch or invalid input returns `422`. |
| Postconditions | Income appears in history and increases balance and Monthly Savings; no expense budget is affected. |
| Related rules | BR-TXN-001–008, BR-TXN-012, BR-DASH-004–006 |
| Related API | `POST /api/v1/transactions` |

## UC-007 Edit Transaction

| Item | Detail |
|---|---|
| Primary actor | Authenticated user |
| Preconditions | Owned nondeleted transaction exists; replacement account/category are valid. |
| Trigger | User submits edited transaction details. |
| Main flow | 1. Service loads owned transaction. 2. New values receive create-level validation. 3. Prior balance and budget contribution are reversed. 4. Updated transaction is persisted. 5. New contribution is applied. 6. Threshold state is recalculated. 7. API returns updated transaction. |
| Alternative flow | Edit may change amount, type, date, account, category, description, or note. |
| Error flow | Unowned/absent resource returns `404`; invalid relationship returns `422`; any failure rolls back all old/new effects. |
| Postconditions | Ledger and every derived view reflect only the updated transaction. |
| Related rules | BR-TXN-009–013, BR-ACCOUNT-005–008, BR-BUDGET-006–011 |
| Related API | `PUT /api/v1/transactions/{id}` |

## UC-008 Delete Transaction

| Item | Detail |
|---|---|
| Primary actor | Authenticated user |
| Preconditions | Owned nondeleted transaction exists. |
| Trigger | User confirms transaction deletion. |
| Main flow | 1. Service loads transaction in user scope. 2. Transaction receives `deletedAt`. 3. Balance contribution is reversed. 4. Dashboard, analytics, and budget usage naturally exclude it. 5. API returns `204`. |
| Alternative flow | Retrying deletion is safe from duplicate financial reversal. |
| Error flow | Absent or unowned UUID returns `404`; atomic failure rolls back deletion and effects. |
| Postconditions | Transaction is absent from normal lists; audit history remains. |
| Related rules | BR-TXN-009, BR-TXN-011–013, BR-BUDGET-011 |
| Related API | `DELETE /api/v1/transactions/{id}` |

## UC-009 Create Monthly Budget

| Item | Detail |
|---|---|
| Primary actor | Authenticated user |
| Preconditions | Active owned/default expense category exists. |
| Trigger | User submits category, month, limit, and warning percentage. |
| Main flow | 1. API parses `YYYY-MM`. 2. Service derives first/last day in user timezone. 3. Category access and expense type are checked. 4. Duplicate user/category/month is checked. 5. Positive limit and warning range are validated. 6. Monthly budget is persisted. 7. Existing expense usage is calculated. 8. API returns `201`. |
| Alternative flow | Warning percentage defaults to 80; name may be omitted. |
| Error flow | Category absence returns `404`; duplicate returns `409`; inactive/income category or invalid values return `422`. |
| Postconditions | Budget appears in usage and dashboard summaries; alerts can trigger on later crossings. |
| Related rules | BR-BUDGET-001–013, BR-CATEGORY-008–010 |
| Related API | `POST /api/v1/budgets`, `GET /api/v1/budgets/usage` |

## UC-010 View Dashboard

| Item | Detail |
|---|---|
| Primary actor | Authenticated user |
| Preconditions | User is active; financial data may be empty. |
| Trigger | User opens the home/dashboard screen. |
| Main flow | 1. API resolves user timezone and currency. 2. Total Balance is calculated from included accounts. 3. Current-month income, expense, and Monthly Savings are aggregated. 4. Budget statuses and unread count are calculated. 5. Recent transactions are loaded. 6. One response is returned. |
| Alternative flow | Empty accounts or months return zero values and empty lists. |
| Error flow | Incompatible currencies return `422` unless grouped/filtered; unexpected reconciliation failure returns sanitized `500`. |
| Postconditions | No financial state changes. |
| Related rules | BR-ACCOUNT-005–006, BR-ACCOUNT-011, BR-DASH-001–007 |
| Related API | `GET /api/v1/dashboard`, `GET /api/v1/dashboard/monthly-summary` |

## UC-011 View Analytics

| Item | Detail |
|---|---|
| Primary actor | Authenticated user |
| Preconditions | User is active. |
| Trigger | User selects analytics view and date/month range. |
| Main flow | 1. API validates inclusive range and maximum duration. 2. Optional account is ownership-checked. 3. Nondeleted transactions are aggregated. 4. Category expenses, income/expense, or Monthly Savings series is returned in chronological order. |
| Alternative flow | Periods with no data return zeros; account and currency filters may narrow results. |
| Error flow | Malformed range returns `400`; unowned account returns `404`; excessive range or currency mismatch returns `422`. |
| Postconditions | No state changes. |
| Related rules | BR-DASH-003–006, BR-ANALYTICS-001–004 |
| Related API | `GET /api/v1/analytics/category-expenses`, `income-vs-expense`, `monthly-savings` |

## UC-012 Update Notification Settings

| Item | Detail |
|---|---|
| Primary actor | Authenticated user |
| Preconditions | User has the registration-created settings record. |
| Trigger | User saves reminder or budget-alert preferences. |
| Main flow | 1. API validates local reminder time and switches. 2. Service loads the caller's settings. 3. MVP in-app fields are replaced. 4. Updated settings are returned. |
| Alternative flow | Daily reminder can be disabled while retaining its configured time. |
| Error flow | Invalid time or inconsistent values return `422`; email/push/quiet-hours fields are rejected as unsupported. |
| Postconditions | Future notification generation follows the new preferences. |
| Related rules | BR-NOTIFICATION-001–006 |
| Related API | `GET/PUT /api/v1/notification-settings` |

## UC-013 Read Notification

| Item | Detail |
|---|---|
| Primary actor | Authenticated user |
| Preconditions | Owned notification exists. |
| Trigger | User opens a notification or selects mark-all-read. |
| Main flow | 1. Service loads owned notification(s). 2. Unread rows become read with current UTC `readAt`. 3. Already-read rows retain their original timestamp. 4. Updated notification or count is returned. |
| Alternative flow | Mark-all updates every unread owned notification in one operation. |
| Error flow | Single absent/unowned UUID returns `404`; mark-all on an empty set returns updated count zero. |
| Postconditions | Unread count decreases consistently. |
| Related rules | BR-NOTIFICATION-007–010 |
| Related API | `PATCH /api/v1/notifications/{id}/read`, `PATCH /api/v1/notifications/read-all` |

## UC-014 Change Password

| Item | Detail |
|---|---|
| Primary actor | Authenticated user |
| Preconditions | User has a valid access token and knows current password. |
| Trigger | User submits current and new passwords. |
| Main flow | 1. Current password is verified. 2. New password policy and difference are checked. 3. New BCrypt hash is persisted. 4. All refresh tokens are revoked atomically. 5. API returns `204`. 6. Client clears local credentials and returns to login. |
| Alternative flow | None. |
| Error flow | Wrong current password returns `401`; policy or same-password failure returns `422`; database failure preserves old password and sessions. |
| Postconditions | Old password and every prior refresh token are unusable. |
| Related rules | BR-AUTH-001–002, BR-AUTH-009 |
| Related API | `PUT /api/v1/auth/password` |

## UC-015 Deactivate Account

| Item | Detail |
|---|---|
| Primary actor | Authenticated user |
| Preconditions | Owned active account exists. |
| Trigger | User confirms account deactivation. |
| Main flow | 1. Service loads account in user scope. 2. `isActive` becomes false. 3. Account remains available for historical reporting. 4. New transactions are blocked. 5. Updated account is returned. |
| Alternative flow | An inactive account may later be reactivated if not deleted. |
| Error flow | Absent/unowned account returns `404`; invalid lifecycle transition returns `422`. |
| Postconditions | Historical balances remain; account is unavailable in new-transaction selectors. |
| Related rules | BR-ACCOUNT-008–010 |
| Related API | `PATCH /api/v1/accounts/{id}/status` |
