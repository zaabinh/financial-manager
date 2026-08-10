# Security Design

## 1. Security Objectives

- Authenticate users without storing reusable plaintext credentials.
- Isolate every user's financial resources.
- Limit the impact of access-token or refresh-token theft.
- Prevent sensitive data from entering logs, traces, source control, or error responses.
- Provide auditable, testable controls suitable for a mobile client and stateless API.

## 2. Trust Boundaries

```text
React Native App (Web / iOS / Android)
  └─ platform secure storage
        │ HTTPS
        ▼
Reverse Proxy / Platform Edge
        │ trusted forwarding headers
        ▼
Spring Security Filter Chain
        │ authenticated principal
        ▼
Application Services
        │ ownership and role authorization
        ▼
PostgreSQL
```

The mobile device, network, and all client-supplied identifiers are untrusted. Database records are trusted only after ownership and lifecycle checks.

## 3. Authentication Flows

### 3.1 Registration

1. Validate and normalize username, email, display name, currency, timezone, and password.
2. Check username/email uniqueness while handling database race conflicts.
3. Hash password with BCrypt cost 12.
4. Create active `USER` and notification settings atomically.
5. When email is present, generate a 256-bit opaque verification token, persist only its SHA-256 hash, and request transactional email delivery.
6. Return profile only; a user with email verifies it before login.

### 3.2 Email Verification

1. The React Native client receives the raw token through a link generated from `CLIENT_PUBLIC_URL`.
2. The client submits the token in the confirmation request body.
3. The service hashes the token, locks its row, and validates expiry, revocation, use state, active user status, and current-email match.
4. The token is consumed, the user is marked verified, and other active verification tokens are revoked atomically.
5. Resend always returns `204`, including for unknown or already verified addresses, and is limited to three normalized-email/IP requests per hour.

Existing users with an email when `V12` is first applied are grandfathered as verified to prevent rollout lockout.

### 3.3 Login

1. Apply IP-and-username rate limiting.
2. Resolve normalized username and verify active status.
3. Verify BCrypt password using constant-time library behavior.
4. Reject a present but unverified email with `403 EMAIL_NOT_VERIFIED`.
5. Issue 15-minute JWT access token.
6. Generate a cryptographically random opaque refresh token.
7. Persist only its SHA-256 hash with seven-day expiration and device metadata.
8. Return the raw token pair once over HTTPS.

### 3.4 Refresh

1. Hash the presented opaque token.
2. Find the stored record and validate user, expiry, revocation, and family state.
3. Revoke the presented token and record `lastUsedAt`.
4. Generate and persist a replacement in the same token family.
5. Link the old token to its replacement.
6. Return a new token pair.

Rotation occurs atomically under row-level concurrency control so two simultaneous refreshes cannot both succeed.

### 3.5 Refresh-Token Reuse

If a revoked token with a replacement is presented:

1. classify it as probable reuse;
2. revoke all active tokens in its family;
3. emit a sanitized high-severity security event;
4. return `401 TOKEN_REUSE_DETECTED`;
5. require login again on affected devices.

### 3.6 Logout and Logout All

- Logout revokes the submitted current-device refresh token and is idempotent.
- Logout-all revokes every active refresh token for the authenticated user.
- Access tokens are not stored; they remain valid only until their short expiration.

### 3.7 Password Change

The current password is verified, the new password is hashed, and every refresh token is revoked in one transaction. The client clears local tokens and requires a new login.

## 4. Access Tokens

| Property | Decision |
|---|---|
| Format | JWT |
| Algorithm | HMAC-SHA-256 |
| Lifetime | 15 minutes |
| Persistence | Never stored in the database |
| Transport | `Authorization: Bearer <token>` |
| Key | At least 256 random bits supplied through `JWT_SECRET` |

Minimal claims:

| Claim | Meaning |
|---|---|
| `sub` | User UUID |
| `role` | `USER` or `ADMIN` |
| `jti` | Unique access-token identifier |
| `iss` | Finance Manager issuer |
| `aud` | Finance Manager mobile API audience |
| `iat` | Issued-at timestamp |
| `exp` | Expiration timestamp |

Tokens do not contain email, display name, financial values, or mutable preferences.

## 5. Refresh Tokens

| Property | Decision |
|---|---|
| Format | Opaque URL-safe random value |
| Entropy | At least 256 bits from a cryptographic RNG |
| Lifetime | Seven days |
| Database value | SHA-256 hash only |
| Rotation | On every successful refresh |
| Family tracking | UUID family ID |
| Revocation | Logout, logout-all, password change, user ban/delete, reuse detection |

Expired and revoked token records are periodically purged after the security audit retention period. User agent and IP are sanitized, access-controlled metadata and must not be exposed through normal user APIs.

## 6. Password Security

- BCrypt cost factor is 12 and is reviewed as hardware changes.
- Passwords contain at least 12 characters and no more than 72 UTF-8 bytes, matching BCrypt's safe input limit.
- Password contains uppercase, lowercase, numeric, and special characters.
- Password must not equal or contain the normalized username.
- New password must differ from the current password.
- Password input, hash, and verification result are never logged.
- Authentication responses never reveal whether the username or password was wrong.
- A future password-reset flow requires a separate time-limited single-use design and is not in MVP scope.

### 6.1 Email Verification Tokens

- Tokens use 32 cryptographically random bytes encoded as URL-safe Base64 without padding.
- Only fixed-length SHA-256 hashes are stored; raw values appear only in the one-time client link.
- Tokens expire after 24 hours, are single-use, and older active tokens are revoked on resend.
- Confirmation validates the token's email snapshot against the current user email.
- Raw tokens, verification URLs, and full email addresses are excluded from logs and analytics.

## 7. Authorization

### 7.1 Roles

| Role | Permissions |
|---|---|
| `USER` | Own profile and owned finance resources; read default categories. |
| `ADMIN` | Explicit future administrative endpoints only; role does not implicitly bypass repository ownership in user APIs. |

No admin endpoints are part of the MVP public contract.

### 7.2 Resource Ownership

Authorization is enforced in layers:

1. Spring Security validates authentication and endpoint role.
2. Application service loads resources by `resourceId + authenticatedUserId`.
3. Cross-module references are validated against the same user.
4. Database FKs, constraints, and selected triggers provide defense in depth.

An absent and an unowned resource both return `404`. Controllers never accept a user ID to choose ownership.

### 7.3 User Status

- `ACTIVE`: normal access.
- `BANNED`: login and protected operations blocked; refresh tokens revoked.
- `DELETED`: login and protected operations blocked; refresh tokens revoked; data hidden from APIs.

Status is checked during login/refresh and, for sensitive operations, against current database state rather than relying only on JWT claims.

## 8. Client Token Storage

- React Native stores both tokens with Expo SecureStore, backed by iOS Keychain or Android Keystore. The web adapter uses browser storage only as an explicit compatibility tradeoff and therefore requires strict CSP, dependency hygiene, and XSS prevention.
- Tokens are never placed in shared preferences, SQLite, analytics events, URLs, crash breadcrumbs, or clipboard.
- The access token is attached only to trusted API origins.
- On refresh failure or reuse detection, both local tokens are erased.
- Screenshots and background snapshots should obscure sensitive financial detail where platform support permits.

## 9. Threat Controls

| Threat | Controls |
|---|---|
| SQL injection | Spring Data parameter binding, no string-concatenated SQL, reviewed native queries, least-privilege DB user. |
| Broken object-level authorization | Scoped repository queries, service ownership checks, cross-user integration tests, `404` response policy. |
| Brute-force login | Initial limit: 5 failed login attempts per username/IP per 15 minutes; progressive temporary blocking and security metrics. |
| Refresh abuse | Initial limit: 20 refresh attempts per IP per minute; rotation and family reuse detection. |
| Verification abuse/enumeration | Uniform resend response, three requests per normalized email/IP per hour, hashed token storage, 24-hour expiry, single use. |
| Token theft | HTTPS, short access lifetime, secure mobile storage, refresh hashing, rotation, revocation. |
| JWT forgery | HMAC-SHA-256, 256-bit secret, algorithm allow-list, issuer/audience validation, no `none` algorithm. |
| Sensitive logging | Structured allow-list fields, redaction filters, no request-body logging on auth or finance writes. |
| Mass assignment | Explicit request DTOs; client cannot set owner, role, source, current balance, token state, or audit fields. |
| Excessive data exposure | Response DTOs omit hashes and internal fields; pagination and bounded date ranges. |
| Dependency vulnerability | Automated dependency and container scanning; timely supported-version upgrades. |
| Denial of service | Request-size bounds, pagination limits, analytics range limits, rate limiting, DB timeouts. |

## 10. CORS and CSRF

- CORS uses an environment-specific allow-list. Development may allow configured local origins; production allows only approved web origins if a web client is introduced.
- Native mobile clients are not governed by browser CORS, but the server configuration remains restrictive.
- The API uses bearer tokens and does not use cookie authentication; CSRF protection may be disabled for stateless API routes.
- If browser cookies are introduced later, CSRF strategy must be redesigned before deployment.
- Allowed methods and headers are explicit; wildcard credentials are prohibited.

## 11. Validation and Request Limits

- JSON request bodies have a conservative maximum size.
- Bean Validation handles shape and length; domain services enforce ownership and business state.
- Pagination maximum is 100.
- Analytics and transaction date ranges are limited to five years.
- Text values are normalized but not interpreted as HTML.
- Error messages contain stable field-level information without stack traces or SQL details.

## 12. Secret and Configuration Management

Canonical environment variables:

| Variable | Purpose | Secret |
|---|---|---:|
| `DATABASE_URL` | PostgreSQL JDBC URL | Environment-sensitive |
| `DATABASE_USERNAME` | Database principal | Yes |
| `DATABASE_PASSWORD` | Database password | Yes |
| `JWT_SECRET` | HMAC signing key | Yes |
| `JWT_ACCESS_EXPIRATION` | Access-token lifetime; 15 minutes | No |
| `JWT_REFRESH_EXPIRATION` | Refresh-token lifetime; seven days | No |
| `ALLOWED_ORIGINS` | CORS allow-list | No |
| `CLIENT_PUBLIC_URL` | Approved React Native web origin used in verification links | No |
| `EMAIL_VERIFICATION_EXPIRATION` | Verification-token lifetime; 24 hours | No |

Production values come from platform secret storage, never committed `.env` files or image layers. Secrets are rotated through an operational procedure that supports a short JWT key-overlap window if required.

### 12.1 Configuration Enforcement

The backend, Docker Compose, and environment template use the canonical `DATABASE_*` and `JWT_*_EXPIRATION` names. Datasource usernames, passwords, and JWT secrets have no application defaults and must be supplied by the environment.

## 13. Logging and Audit Events

Log:

- correlation ID, route template, status, duration, module;
- login success/failure without password or username enumeration detail;
- token rotation/reuse/revocation identifiers using record UUIDs, never token values;
- password changes, user status changes, and logical deletion;
- authorization denials and rate-limit events;
- balance-reconciliation discrepancies.

Do not log:

- passwords, raw tokens, JWT authorization headers, token hashes;
- full financial notes or request bodies;
- complete email/IP/user-agent values unless required and access-controlled;
- SQL parameters containing personal or authentication data.

## 14. Security Testing and Operations

- Unit tests cover token claims, expiry, hashing, rotation, and password policy.
- Integration tests cover cross-user access, revoked/expired/reused tokens, status changes, and concurrent refresh.
- CI performs dependency, secret, SAST, and container-image scans.
- Production alerts cover login spikes, reuse detection, authorization spikes, repeated `500` errors, and secret/config failures.
- A security incident procedure supports token-family revocation, all-user session revocation when necessary, secret rotation, evidence preservation, and user communication.
