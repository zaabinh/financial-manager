# REST API Design

## 1. Conventions

- Base path: `/api/v1`
- Media type: `application/json`
- Authentication: `Authorization: Bearer <access-token>`
- Resource identifiers: UUID strings
- Field naming: lower camel case
- Dates: ISO `YYYY-MM-DD`
- Months: ISO `YYYY-MM`
- Local times: `HH:mm:ss`
- Timestamps: ISO-8601 UTC, for example `2026-07-16T10:00:00Z`
- Money: JSON decimal numbers mapped to exact decimal types, never binary floating-point calculations
- Currency: uppercase ISO-4217 code

All endpoints require authentication unless explicitly marked public. Owned resources that do not exist or belong to another user return `404` to avoid disclosing identifiers.

## 2. Standard Responses

### 2.1 Success

```json
{
  "success": true,
  "data": {},
  "message": "Request completed successfully",
  "timestamp": "2026-07-16T10:00:00Z"
}
```

`data` may be an object, list, page, scalar, or null. A `204 No Content` response has no body and therefore does not use the envelope.

### 2.2 Error

```json
{
  "success": false,
  "error": {
    "code": "RESOURCE_NOT_FOUND",
    "message": "Transaction not found",
    "fieldErrors": [
      {
        "field": "amount",
        "code": "POSITIVE",
        "message": "Amount must be greater than zero"
      }
    ]
  },
  "timestamp": "2026-07-16T10:00:00Z",
  "path": "/api/v1/transactions/99162fd1-7a39-475f-b06f-a4c7ef4ec984"
}
```

### 2.3 Status Semantics

| Status | Meaning |
|---|---|
| `200` | Successful read or update with a response body. |
| `201` | Resource created; `Location` header identifies it. |
| `204` | Successful operation with no response body. |
| `400` | Malformed JSON, invalid UUID/date syntax, unsupported sort field, or invalid query shape. |
| `401` | Missing, invalid, expired, revoked, or reused authentication token. |
| `403` | Authenticated principal lacks role permission or is banned/deleted. |
| `404` | Resource is absent or not owned by the caller. |
| `409` | Username/email/name uniqueness conflict or duplicate monthly budget. |
| `422` | Syntactically valid request violates field or domain rules. |
| `429` | Authentication or other rate limit exceeded. |
| `500` | Unexpected server failure; response contains a correlation ID but no internal details. |

## 3. Pagination and Sorting

List endpoints supporting pagination accept:

| Parameter | Default | Rules |
|---|---:|---|
| `page` | 0 | Zero-based nonnegative integer. |
| `size` | 20 | From 1 through 100. |
| `sort` | Endpoint-specific | Repeatable `field,direction`; direction is `asc` or `desc`. |

Page response:

```json
{
  "content": [],
  "page": 0,
  "size": 20,
  "totalElements": 0,
  "totalPages": 0,
  "first": true,
  "last": true
}
```

## 4. Shared Resource Shapes

### 4.1 User

```json
{
  "id": "2aa2ac5a-02cc-47f4-87bf-20a48f68e7df",
  "username": "minh.nguyen",
  "email": "minh@example.com",
  "displayName": "Minh Nguyen",
  "role": "USER",
  "currency": "VND",
  "timezone": "Asia/Ho_Chi_Minh",
  "createdAt": "2026-07-16T10:00:00Z"
}
```

### 4.2 Account

```json
{
  "id": "3ced4b48-34aa-4468-9042-bdd2f66ae5af",
  "name": "Daily Cash",
  "type": "CASH",
  "initialBalance": 500000.0000,
  "currentBalance": 350000.0000,
  "currency": "VND",
  "isActive": true,
  "includeInTotal": true,
  "displayOrder": 0,
  "createdAt": "2026-07-16T10:00:00Z",
  "updatedAt": "2026-07-16T10:00:00Z"
}
```

### 4.3 Category

```json
{
  "id": "ca0815d8-077b-4271-b256-3ac2bc58cc93",
  "name": "Food",
  "transactionType": "EXPENSE",
  "icon": "utensils",
  "color": "#EF5350",
  "isDefault": true,
  "isActive": true,
  "displayOrder": 10
}
```

### 4.4 Transaction

```json
{
  "id": "99162fd1-7a39-475f-b06f-a4c7ef4ec984",
  "amount": 150000.0000,
  "transactionType": "EXPENSE",
  "transactionDate": "2026-07-16",
  "transactionTime": "12:30:00",
  "description": "Lunch",
  "note": null,
  "source": "MANUAL",
  "account": {
    "id": "3ced4b48-34aa-4468-9042-bdd2f66ae5af",
    "name": "Daily Cash"
  },
  "category": {
    "id": "ca0815d8-077b-4271-b256-3ac2bc58cc93",
    "name": "Food"
  },
  "createdAt": "2026-07-16T10:00:00Z",
  "updatedAt": "2026-07-16T10:00:00Z"
}
```

## 5. Authentication API

Authentication endpoints are rate-limited. Register, login, and refresh are public; the others require a valid access token.

| Endpoint | Purpose / Auth | Request | Success | Validation and Errors |
|---|---|---|---|---|
| `POST /auth/register` | Create an active user. Public. | Body: `username`, optional `email`, `displayName`, `password`, optional `currency`, optional `timezone`. | `201` User; `Location: /api/v1/users/me`. | `422` invalid fields/password; `409` username or email conflict. |
| `POST /auth/login` | Issue a token pair. Public. | Body: `username`, `password`, optional `deviceName`. | `200` token response. | `401` invalid credentials or inactive user; `429` rate limit. |
| `POST /auth/refresh` | Rotate refresh token and issue a new pair. Public. | Body: `refreshToken`. | `200` token response. | `401` expired, revoked, unknown, or reused token. |
| `POST /auth/logout` | Revoke current refresh token. Bearer required. | Body: `refreshToken`. | `204`. | `401` invalid access token; unknown/already revoked refresh token remains idempotent. |
| `POST /auth/logout-all` | Revoke all user refresh tokens. Bearer required. | No body. | `204`. | `401` invalid access token. |
| `PUT /auth/password` | Change password and revoke all sessions. Bearer required. | Body: `currentPassword`, `newPassword`. | `204`. | `401` current password wrong; `422` policy failure or same password. |

Register example:

```json
{
  "username": "minh.nguyen",
  "email": "minh@example.com",
  "displayName": "Minh Nguyen",
  "password": "StrongPassword#2026",
  "currency": "VND",
  "timezone": "Asia/Ho_Chi_Minh"
}
```

Token response:

```json
{
  "accessToken": "<jwt>",
  "refreshToken": "<opaque-token>",
  "tokenType": "Bearer",
  "accessTokenExpiresIn": 900,
  "refreshTokenExpiresIn": 604800,
  "user": {}
}
```

## 6. User API

| Endpoint | Purpose / Auth | Request | Success | Validation and Errors |
|---|---|---|---|---|
| `GET /users/me` | Return caller profile. Bearer required. | No body or query. | `200` User. | `401` invalid token; `403` inactive principal. |
| `PUT /users/me` | Update profile and application preferences. Bearer required. | Body: `displayName`, optional `email`, `currency`, and `timezone`. | `200` updated User. | `422` invalid fields; `409` email conflict. |
| `DELETE /users/me` | Logically delete profile. Bearer required. | Body: `currentPassword`. | `204`; all refresh tokens revoked. | `401` wrong password/token; `422` malformed confirmation. |

Application preferences are updated through notification settings and the profile settings fields:

```json
{
  "displayName": "Minh Nguyen",
  "email": "minh@example.com",
  "currency": "VND",
  "timezone": "Asia/Ho_Chi_Minh"
}
```

`currency` and `timezone` are accepted by `PUT /users/me` when settings are edited. Changing currency never converts historical values.

## 7. Account API

Account write body:

| Field | Create | Update | Rules |
|---|---:|---:|---|
| `name` | Required | Required | 1–120 visible characters. |
| `type` | Required | Required | `CASH`, `BANK`, or `SAVINGS`. |
| `initialBalance` | Optional | Required | Decimal, defaults to 0 on create. |
| `currency` | Optional | Required | ISO-4217, defaults to user currency. |
| `includeInTotal` | Optional | Required | Defaults true. |
| `displayOrder` | Optional | Required | Nonnegative, defaults 0. |

| Endpoint | Purpose / Auth | Request | Success | Validation and Errors |
|---|---|---|---|---|
| `GET /accounts` | List owned accounts. | Query: optional `isActive`, `includeInTotal`, `type`; sort by `displayOrder,name`. | `200` list of Accounts. | `400` invalid filter/type. |
| `GET /accounts/{id}` | Get one owned account. | UUID path. | `200` Account. | `400` malformed UUID; `404` absent/unowned. |
| `POST /accounts` | Create account. | Account create body. | `201` Account with `Location`. | `409` active name conflict; `422` field/domain violation. |
| `PUT /accounts/{id}` | Replace editable metadata. | UUID path and update body; excludes `currentBalance`. | `200` Account. | `404` absent/unowned; `409` name conflict; `422` invalid values. |
| `DELETE /accounts/{id}` | Remove unused account or soft-delete/deactivate account with history. | UUID path. | `204`. | `404` absent/unowned; `422` account cannot enter requested lifecycle state. |
| `PATCH /accounts/{id}/status` | Activate or deactivate account. | Body: `{"isActive": false}`. | `200` Account. | `404` absent/unowned; `422` invalid transition. |
| `GET /accounts/{id}/balance` | Return authoritative calculated and optional cached balance. | UUID path. | `200` balance response. | `404` absent/unowned; `500` reconciliation failure. |

Balance response:

```json
{
  "accountId": "3ced4b48-34aa-4468-9042-bdd2f66ae5af",
  "currency": "VND",
  "initialBalance": 500000.0000,
  "income": 200000.0000,
  "expense": 350000.0000,
  "currentBalance": 350000.0000,
  "asOf": "2026-07-16T10:00:00Z"
}
```

## 8. Category API

Category write body contains `name`, `transactionType`, optional `icon`, optional `color`, and optional nonnegative `displayOrder`. Clients cannot set `isDefault`.

| Endpoint | Purpose / Auth | Request | Success | Validation and Errors |
|---|---|---|---|---|
| `GET /categories` | List default and owned categories. | Filters: `transactionType`, `isDefault`, `isActive`. | `200` Category list. | `400` invalid filter. |
| `GET /categories/{id}` | Get applicable category. | UUID path. | `200` Category. | `404` absent/inaccessible. |
| `POST /categories` | Create user category. | Category body. | `201` Category with `Location`. | `409` duplicate owner/name/type; `422` invalid fields. |
| `PUT /categories/{id}` | Update owned category. | UUID path and full editable body. | `200` Category. | `403` default category; `404` absent/unowned; `409` duplicate; `422` incompatible type change. |
| `DELETE /categories/{id}` | Delete unused or soft-delete category with history. | UUID path. | `204`. | `403` default category; `404` absent/unowned. |
| `PATCH /categories/{id}/status` | Activate/deactivate owned category. | Body: `{"isActive": false}`. | `200` Category. | `403` default category; `404` absent/unowned; `422` invalid transition. |

## 9. Transaction API

Transaction create/update body:

| Field | Required | Rules |
|---|---:|---|
| `amount` | Yes | Decimal greater than zero, maximum four fractional digits. |
| `transactionType` | Yes | `INCOME` or `EXPENSE`. |
| `transactionDate` | Yes | ISO date. |
| `transactionTime` | No | Local time; current user-local time when omitted. |
| `accountId` | Yes | Active owned account UUID. |
| `categoryId` | Yes | Active applicable category matching type. |
| `description` | No | Up to 500 characters. |
| `note` | No | Up to 2,000 characters. |

`source` is server-controlled as `MANUAL`.

| Endpoint | Purpose / Auth | Request | Success | Validation and Errors |
|---|---|---|---|---|
| `GET /transactions` | Page owned transactions. | Filters: `dateFrom`, `dateTo`, `accountId`, `categoryId`, `transactionType`, `search`; page/sort; default sort `transactionDate,desc`, `transactionTime,desc`. | `200` page of Transactions. | `400` malformed range/filter/sort; `422` range exceeds five years. |
| `GET /transactions/{id}` | Get one owned transaction. | UUID path. | `200` Transaction. | `404` absent/unowned. |
| `POST /transactions` | Record manual transaction. | Create body. | `201` Transaction with `Location`. | `404` account/category inaccessible; `422` amount, inactive resource, ownership, or type mismatch. |
| `PUT /transactions/{id}` | Replace editable transaction data. | UUID path and update body. | `200` Transaction. | `404` transaction/account/category inaccessible; `422` domain violation. |
| `DELETE /transactions/{id}` | Soft-delete transaction and reverse effects. | UUID path. | `204`. | `404` absent/unowned; retries remain safe. |

## 10. Budget API

Budget write body:

```json
{
  "categoryId": "ca0815d8-077b-4271-b256-3ac2bc58cc93",
  "month": "2026-07",
  "name": "Food budget",
  "limitAmount": 3000000.0000,
  "warningPercentage": 80
}
```

The service derives calendar-month start and end dates. Nonmonthly period types and rollover are not accepted.

| Endpoint | Purpose / Auth | Request | Success | Validation and Errors |
|---|---|---|---|---|
| `GET /budgets` | List owned budgets. | Filters: optional `month`, `categoryId`, `isActive`; page/sort. | `200` budget page. | `400` malformed filters. |
| `GET /budgets/{id}` | Get budget with usage. | UUID path. | `200` Budget. | `404` absent/unowned. |
| `POST /budgets` | Create monthly expense budget. | Budget body. | `201` Budget with `Location`. | `404` category inaccessible; `409` duplicate month/category; `422` income/inactive category or invalid values. |
| `PUT /budgets/{id}` | Update owned budget. | UUID path and budget body. | `200` Budget. | `404` absent/unowned; `409` duplicate; `422` invalid rule. |
| `DELETE /budgets/{id}` | Soft-delete budget. | UUID path. | `204`. | `404` absent/unowned. |
| `GET /budgets/usage` | Summarize budget usage. | Required `month`; optional `categoryId`, `status`. | `200` usage list and totals. | `400` malformed month/status. |

Budget response:

```json
{
  "id": "5f7713f5-ec26-4a66-9d00-c06f96e47489",
  "category": {},
  "month": "2026-07",
  "limitAmount": 3000000.0000,
  "warningPercentage": 80,
  "spentAmount": 2500000.0000,
  "remainingAmount": 500000.0000,
  "usagePercentage": 83.33,
  "status": "WARNING",
  "isActive": true
}
```

## 11. Dashboard API

| Endpoint | Purpose / Auth | Request | Success | Validation and Errors |
|---|---|---|---|---|
| `GET /dashboard` | Current financial overview. | Optional `currency`; otherwise user default. | `200` total balance, current-month values, budget counts, unread count, recent transactions. | `422` accounts contain incompatible currencies for an ungrouped total. |
| `GET /dashboard/monthly-summary` | Summary for one month. | Required `month=YYYY-MM`; optional `currency`. | `200` monthly summary. | `400` malformed month; `422` unsupported currency aggregation. |

Dashboard response distinguishes values explicitly:

```json
{
  "currency": "VND",
  "totalBalance": 12500000.0000,
  "monthlyIncome": 20000000.0000,
  "monthlyExpense": 8500000.0000,
  "monthlySavings": 11500000.0000,
  "budgetSummary": {
    "safe": 4,
    "warning": 1,
    "exceeded": 0
  },
  "unreadNotificationCount": 2,
  "recentTransactions": []
}
```

## 12. Analytics API

All analytics ranges are inclusive and limited to five years.

| Endpoint | Purpose / Auth | Request | Success | Validation and Errors |
|---|---|---|---|---|
| `GET /analytics/category-expenses` | Group expenses by category. | Required `dateFrom`, `dateTo`; optional `accountId`, `currency`. | `200` categories with amount and percentage. | `400` malformed range; `404` account inaccessible; `422` excessive range/currency mismatch. |
| `GET /analytics/income-vs-expense` | Monthly income/expense series. | Required `monthFrom`, `monthTo`; optional `accountId`, `currency`. | `200` ordered monthly points. | Same range, ownership, and currency errors. |
| `GET /analytics/monthly-savings` | Monthly Savings trend. | Required `monthFrom`, `monthTo`; optional `accountId`, `currency`. | `200` ordered `income`, `expense`, `monthlySavings` points. | Same range, ownership, and currency errors. |

## 13. Notification API

| Endpoint | Purpose / Auth | Request | Success | Validation and Errors |
|---|---|---|---|---|
| `GET /notifications` | Page owned notifications newest first. | Optional `isRead`, `notificationType`; page/sort. | `200` notification page. | `400` invalid filter/sort. |
| `GET /notifications/unread-count` | Return unread count. | No body/query. | `200` `{"count": 2}`. | Authentication errors only. |
| `PATCH /notifications/{id}/read` | Mark one notification read. | UUID path; no body. | `200` updated notification. | `404` absent/unowned; idempotent when already read. |
| `PATCH /notifications/read-all` | Mark all owned notifications read. | No body. | `200` `{"updatedCount": 4}`. | Authentication errors only. |
| `DELETE /notifications/{id}` | Delete owned notification. | UUID path. | `204`. | `404` absent/unowned. |

Notification response:

```json
{
  "id": "0d833e56-4d9b-4ab9-99fc-91c0e90bdc4a",
  "title": "Budget warning",
  "content": "Food spending reached 80% of your July budget.",
  "notificationType": "BUDGET_WARNING",
  "relatedEntityType": "BUDGET",
  "relatedEntityId": "5f7713f5-ec26-4a66-9d00-c06f96e47489",
  "isRead": false,
  "createdAt": "2026-07-16T10:00:00Z",
  "readAt": null
}
```

## 14. Notification Settings API

MVP accepts only in-app reminder and budget controls. Email, push, and quiet-hours fields are not exposed.

| Endpoint | Purpose / Auth | Request | Success | Validation and Errors |
|---|---|---|---|---|
| `GET /notification-settings` | Return caller settings. | No body/query. | `200` settings. | Authentication errors only. |
| `PUT /notification-settings` | Replace caller settings. | Body below. | `200` updated settings. | `422` invalid local time or inconsistent switches. |

```json
{
  "dailyReminderEnabled": true,
  "dailyReminderTime": "20:00:00",
  "budgetWarningEnabled": true,
  "budgetExceededEnabled": true,
  "systemNotificationsEnabled": true,
  "inAppEnabled": true
}
```

## 15. Stable Error Codes

| Code | Typical Status |
|---|---:|
| `VALIDATION_FAILED` | 422 |
| `MALFORMED_REQUEST` | 400 |
| `INVALID_CREDENTIALS` | 401 |
| `TOKEN_EXPIRED` | 401 |
| `TOKEN_REVOKED` | 401 |
| `TOKEN_REUSE_DETECTED` | 401 |
| `ACCESS_DENIED` | 403 |
| `RESOURCE_NOT_FOUND` | 404 |
| `USERNAME_ALREADY_EXISTS` | 409 |
| `EMAIL_ALREADY_EXISTS` | 409 |
| `DUPLICATE_RESOURCE` | 409 |
| `DUPLICATE_MONTHLY_BUDGET` | 409 |
| `INACTIVE_RESOURCE` | 422 |
| `CATEGORY_TYPE_MISMATCH` | 422 |
| `CURRENCY_AGGREGATION_UNSUPPORTED` | 422 |
| `INTERNAL_ERROR` | 500 |
| `RATE_LIMIT_EXCEEDED` | 429 |

## 16. OpenAPI Contract

Springdoc-generated OpenAPI documentation is the executable contract. It must:

- include bearer authentication and reusable standard error schemas;
- describe every request, response, enum, format, and validation bound in this document;
- hide password hashes, token hashes, and internal fields;
- publish staging Swagger UI only behind appropriate access controls;
- be checked in CI for unintended breaking changes before release.
