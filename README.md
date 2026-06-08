# GenLink KH — Authentication & Security Flow

> This document describes the current authentication and security flow for the GenLink KH backend.

---

## 1. Overview

GenLink KH uses an authentication flow based on:

- Email/password registration and login
- Email OTP verification
- JWT access tokens
- JWT refresh tokens
- Refresh token rotation
- BCrypt password hashing
- Hashed OTP storage
- Hashed refresh token storage
- Account status control
- Security audit logging
- Rate limiting for sensitive auth actions

The authentication system is designed to protect user accounts while keeping the mobile client integration simple and secure.

---

## 2. Account Status Lifecycle

Each user account is controlled by `accountStatus`.

| Status | Meaning |
|---|---|
| `PENDING` | User registered but has not verified email OTP yet |
| `ACTIVE` | User verified email and can access the app |
| `BANNED` | User is blocked and cannot login or refresh tokens |
| `DELETED` | User account is deleted or disabled permanently |

A newly registered user starts as:

```text
accountStatus = PENDING
emailVerified = false
```

After successful OTP verification:

```text
accountStatus = ACTIVE
emailVerified = true
```

---

## 3. Register Flow

### Endpoint

```http
POST /api/v1/auth/register
```

### Flow

```text
Client submits register request
→ Backend validates request
→ Backend checks duplicate email, username, and phone
→ Backend validates password policy
→ Backend hashes password using BCrypt
→ Backend creates user with PENDING status
→ Backend generates OTP
→ Backend stores OTP hash in database
→ Backend sends OTP email using preferred language
→ User cannot login until OTP is verified
```

### Default Values

If not provided:

```text
workStatus = STUDENT
preferredLanguage = ENGLISH
accountStatus = PENDING
emailVerified = false
```

### Security Notes

- Raw password is never stored.
- Password is stored as BCrypt hash only.
- OTP is stored as hash only.
- Duplicate email, username, and phone are checked before account creation.

---

## 4. Verify OTP Flow

### Endpoint

```http
POST /api/v1/auth/verify-otp
```

### Flow

```text
Client submits email + OTP
→ Backend finds OTP record
→ Backend checks purpose
→ Backend checks expiry
→ Backend checks usage status
→ Backend checks attempt limit
→ Backend compares OTP hash
→ If valid, OTP is marked verified/used
→ User becomes ACTIVE
→ emailVerified becomes true
→ Backend returns access token and refresh token
```

### OTP Rules

| Rule | Value |
|---|---|
| Max verification attempts | 5 |
| OTP storage | Hashed |
| OTP reuse | Not allowed |
| Expired OTP | Rejected |
| Already-used OTP | Rejected |

### OTP Error Codes

```text
OTP_INVALID
OTP_EXPIRED
OTP_ALREADY_USED
OTP_TOO_MANY_ATTEMPTS
```

---

## 5. Resend Verification OTP Flow

### Endpoint

```http
POST /api/v1/auth/resend-verification-otp
```

### Flow

```text
Client submits email
→ Backend checks if account is eligible
→ Backend applies resend rate limit
→ Backend invalidates previous active REGISTER OTPs
→ Backend generates new OTP
→ Backend stores OTP hash
→ Backend sends localized OTP email
→ Backend returns generic success response
```

### Resend Rules

| Rule | Value |
|---|---|
| Max resend attempts | 3 per 15 minutes |
| Minimum resend wait time | 60 seconds |
| Purpose | REGISTER |
| Email existence disclosure | Not allowed |

### Generic Response

```text
If the account is eligible, a verification OTP has been sent.
```

### Error Codes

```text
OTP_RESEND_TOO_SOON
OTP_RESEND_LIMIT_EXCEEDED
AUTH_ACCOUNT_NOT_ALLOWED
```

---

## 6. Login Flow

### Endpoint

```http
POST /api/v1/auth/login
```

### Flow

```text
Client submits email and password
→ Backend checks login rate limit
→ Backend loads user by email
→ Backend rejects banned/deleted/pending/unverified users
→ Backend verifies password using BCrypt
→ Backend generates JWT access token
→ Backend generates JWT refresh token
→ Backend stores only refresh token hash in DB
→ Backend stores device/session metadata
→ Backend returns user info, access token, and raw refresh token
```

### Login Rejection Rules

Login is rejected if the user is:

```text
BANNED
DELETED
PENDING
emailVerified = false
```

### Login Rate Limit

| Rule | Value |
|---|---|
| Max failed login attempts | 5 |
| Window | 15 minutes |
| Key | email + client IP |
| Current implementation | In-memory |
| Future improvement | Redis for distributed deployment |

### Login Error Codes

```text
AUTH_INVALID_CREDENTIALS
AUTH_ACCOUNT_PENDING
AUTH_ACCOUNT_BANNED
AUTH_ACCOUNT_DELETED
AUTH_EMAIL_NOT_VERIFIED
AUTH_ACCOUNT_NOT_ALLOWED
AUTH_TOO_MANY_ATTEMPTS
```

---

## 7. Access Protected APIs

### Client Request Header

```http
Authorization: Bearer ACCESS_TOKEN
```

### Flow

```text
Client sends protected API request
→ JwtAuthenticationFilter reads access token
→ Backend validates JWT signature and expiry
→ Backend loads user by email from token subject
→ Backend sets authenticated user in SecurityContext
→ Protected endpoint can access current authenticated user
```

### Security Notes

- JWT access token is short-lived.
- JWT does not store profile data.
- JWT does not store preferred language.
- JWT does not store full account state.
- SecurityContext is created only after JWT validation.

---

## 8. Refresh Token Flow

### Endpoint

```http
POST /api/v1/auth/refresh-token
```

### Flow

```text
Client submits raw refresh token
→ Backend validates refresh token JWT
→ Backend hashes raw refresh token
→ Backend finds token hash in DB
→ Backend checks expiry
→ Backend checks revoked status
→ Backend checks user account status
→ Backend detects refresh token reuse
→ Backend revokes old refresh token
→ Backend generates new access token
→ Backend generates new refresh token
→ Backend stores only new refresh token hash
→ Backend returns new access token and new refresh token
```

### Refresh Token Security

| Item | Rule |
|---|---|
| Raw refresh token | Returned once to client |
| Stored refresh token | Hashed only |
| Rotation | Enabled |
| Reuse detection | Enabled |
| Old token after refresh | Revoked |
| Expired token | Rejected |
| Revoked token reuse | Revoke all user refresh tokens |

### Refresh Rejection Rules

Refresh is rejected if:

```text
token not found
token expired
token revoked
user is banned
user is deleted
user is pending
user email is not verified
```

### Refresh Token Error Codes

```text
TOKEN_INVALID
TOKEN_EXPIRED
TOKEN_REVOKED
TOKEN_REUSE_DETECTED
AUTH_ACCOUNT_NOT_ALLOWED
```

---

## 9. Refresh Token Reuse Detection

If a revoked refresh token is used again:

```text
Backend treats it as possible token theft
→ Backend revokes all refresh tokens for that user
→ Backend returns TOKEN_REUSE_DETECTED
→ Client must force logout and require login again
```

This protects users if an old refresh token was stolen.

---

## 10. Get Current User Flow

### Endpoint

```http
GET /api/v1/auth/me
```

### Requirement

Requires valid access token.

### Flow

```text
Client sends access token
→ Backend validates JWT
→ Backend reads current user from SecurityContext
→ Backend returns safe user DTO
```

### Safe Response Rule

The response must not expose:

```text
passwordHash
tokenHash
otpHash
OTP data
refresh token data
security-sensitive internal fields
```

---

## 11. Logout Flow

### Endpoint

```http
POST /api/v1/auth/logout
```

### Requirement

Requires valid access token.

### Flow

```text
Client sends logout request
→ Backend reads current user from SecurityContext
→ Backend revokes refresh tokens for that user
→ Client removes access token and refresh token locally
```

### Security Notes

- Access token remains valid until expiry.
- Refresh token is blocked after logout.
- User must login again after access token expires.

---

## 12. Forgot Password Flow

### Endpoint

```http
POST /api/v1/auth/forgot-password
```

### Flow

```text
Client submits email
→ Backend checks user internally
→ Backend returns generic response
→ If eligible user exists, backend generates FORGOT_PASSWORD OTP
→ Backend stores OTP hash
→ Backend sends OTP email using user preferred language
```

### Generic Response

```text
If this email exists, a password reset OTP has been sent.
```

### Security Notes

- Backend does not reveal whether the email exists.
- BANNED or DELETED users do not receive OTP.
- Raw OTP is never stored.
- OTP email follows user preferred language.

---

## 13. Reset Password Flow

### Endpoint

```http
POST /api/v1/auth/reset-password
```

### Flow

```text
Client submits email, OTP, and new password
→ Backend verifies FORGOT_PASSWORD OTP
→ Backend validates password policy
→ Backend hashes new password with BCrypt
→ Backend saves new password hash
→ Backend marks OTP as used
→ Backend revokes existing refresh tokens
→ User must login again
```

### Password Policy

| Rule | Value |
|---|---|
| Minimum length | 8 characters |
| Maximum length | 72 characters |
| Required | At least 1 letter |
| Required | At least 1 number |

### Error Code

```text
AUTH_WEAK_PASSWORD
```

---

## 14. Security Audit Logging

The system records important auth/security events.

### Audit Events

```text
REGISTER_SUCCESS
REGISTER_FAILED
VERIFY_OTP_SUCCESS
VERIFY_OTP_FAILED
RESEND_OTP_REQUEST
RESEND_OTP_BLOCKED
LOGIN_SUCCESS
LOGIN_FAILED
LOGIN_BLOCKED
REFRESH_SUCCESS
REFRESH_FAILED
REFRESH_REUSE_DETECTED
LOGOUT
FORGOT_PASSWORD_REQUEST
RESET_PASSWORD_SUCCESS
RESET_PASSWORD_FAILED
```

### Audit Data

```text
auditId
userId nullable
email nullable
eventType
ipAddress nullable
userAgent nullable
success
failureReason nullable
createdAt
```

### Rule

Audit logging failure must not break the authentication flow.

---

## 15. Device / Session Tracking

Refresh token records store session metadata.

### Tracked Fields

```text
deviceInfo
ipAddress
userAgent
lastUsedAt
createdAt
expiresAt
revokedAt
```

### Usage

| Action | Behavior |
|---|---|
| Login | Save device metadata |
| Refresh | Update `lastUsedAt` |
| Logout | Revoke refresh token/session |
| Future | Active sessions screen / logout all devices |

---

## 16. Request Metadata

The backend extracts request metadata from HTTP requests.

### Metadata

```text
client IP
user agent
device info
```

### IP Source Priority

```text
X-Forwarded-For
X-Real-IP
request.getRemoteAddr()
```

---

## 17. Token Storage Summary

| Secret Type | Storage Rule |
|---|---|
| Password | BCrypt hash only |
| OTP | Hash only |
| Refresh token | Hash only in database |
| Access token | JWT returned to client |
| Raw refresh token | Returned once to client |
| JWT claims | Minimal and stable |

---

## 18. Standard Auth Error Codes

```text
AUTH_INVALID_CREDENTIALS
AUTH_ACCOUNT_PENDING
AUTH_ACCOUNT_BANNED
AUTH_ACCOUNT_DELETED
AUTH_EMAIL_NOT_VERIFIED
AUTH_ACCOUNT_NOT_ALLOWED
AUTH_TOO_MANY_ATTEMPTS
AUTH_WEAK_PASSWORD
OTP_INVALID
OTP_EXPIRED
OTP_ALREADY_USED
OTP_TOO_MANY_ATTEMPTS
OTP_RESEND_TOO_SOON
OTP_RESEND_LIMIT_EXCEEDED
TOKEN_INVALID
TOKEN_EXPIRED
TOKEN_REVOKED
TOKEN_REUSE_DETECTED
USER_NOT_FOUND
VALIDATION_ERROR
```

Flutter should translate these error codes into Khmer and English user-facing messages.

---

## 19. Language Behavior

Auth supports user language preference.

### Rules

```text
Register uses preferredLanguage if provided
If not provided, preferredLanguage defaults to ENGLISH
Forgot password email uses user preferredLanguage
OTP email uses user preferredLanguage
JWT does not store preferredLanguage
Login and refresh do not overwrite preferredLanguage
```

---

## 20. Security Summary

```text
Access token: short-lived JWT
Refresh token: JWT raw value returned once, hash stored in DB
Password: BCrypt hash only
OTP: hash stored in DB
Login: blocked by account status and email verification
Refresh: blocked by account status and email verification
Refresh rotation: enabled
Refresh reuse detection: enabled
Security audit logs: enabled
JWT claims: minimal
SecurityContext: created only after JWT validation
```

---

## 21. Future Improvements

```text
Replace in-memory login rate limiter with Redis
Add active sessions screen
Add logout from one device
Add logout from all devices
Add suspicious login notification
Add email change verification flow
Add phone verification flow
Add admin account lock/unlock management
```

---

## 22. Local Runtime Notes

If `bootRun` fails because of datasource configuration, make sure the database URL starts with:

```text
jdbc:postgresql://
```

Example:

```env
DB_URL=jdbc:postgresql://localhost:5432/genlink_kh
DB_USERNAME=postgres
DB_PASSWORD=postgres
```

If `bootRun` fails because of weak JWT secret, use a strong secret:

```env
JWT_SECRET=your-very-long-secure-secret-key-at-least-32-characters-change-me-now
```

---

## 23. Developer Rules

Do not expose:

```text
passwordHash
tokenHash
otpHash
refresh token DB data
security audit internals
```

Do not store raw:

```text
password
OTP
refresh token
```

Do not add these into JWT:

```text
profile data
preferredLanguage
account status
large user data
```

Keep auth responses safe, small, and mobile-friendly.
