# GenLink KH — Mail Service & Testing in Development

> How OTP / transactional email is sent, and how to test it locally without a
> production mail account. Backend only.

---

## 1. Overview

`EmailServiceImpl` (`shared/service`) sends OTP emails for registration and password
reset. `JavaMailSender` is auto-configured from `spring.mail.*`. Templates live under
`resources/templates/email/` and are chosen by language (en/km) and purpose
(register / forgot-password).

Two config keys control it, both read via `@Value` with safe defaults:

| Key | Default | Meaning |
|---|---|---|
| `app.mail.enabled` | `true` | When `false`, log-only mode — the OTP is logged and no SMTP connection is made. |
| `app.mail.from` | `${spring.mail.username}` → `no-reply@campuslinkkh.edu.kh` | The From header. A **verified sender**, deliberately separate from the SMTP login. |

`app.mail.enabled` defaults to **`false` in the `dev` profile** (`application-dev.yaml`),
so a fresh dev checkout tests OTP flows with no mail server at all.

---

## 2. The bug this fixed

The From header used to be `spring.mail.username` (the SMTP login). That works for Gmail,
where the From must equal the authenticated account, but **breaks with Brevo**: a Brevo SMTP
login (e.g. `8xxxxxx@smtp-brevo.com`, or a value with characters not legal in a From header)
is not a valid sender address. `MimeMessageHelper.setFrom(login)` then threw

```
jakarta.mail.internet.AddressException: Domain contains illegal character
```

**before any network call** — so it looked like a Brevo/SMTP failure but was actually
address parsing. The From is now its own property (`app.mail.from`) that falls back to the
login only when unset, so a bad or provider-shaped login can no longer corrupt the header.

---

## 3. Testing email in development — three ways

### A. Log-only (default, no mail server)

`MAIL_ENABLED=false` (the dev default). Trigger a flow that issues an OTP — register, or
`POST /api/v1/auth/forgot-password` — and read the code from the app console:

```
EMAIL OTP FOR makara00814@gmail.com: 099058
```

Use that OTP in the verify / reset request. Nothing is sent; nothing can fail to send.

### B. Local SMTP catcher (see the real rendered email)

Run a throwaway SMTP server that captures mail in a web UI instead of delivering it —
[Mailpit](https://github.com/axllent/mailpit) or MailHog:

```bash
docker run -d -p 1025:1025 -p 8025:8025 axllent/mailpit
```

Then set:

```
MAIL_ENABLED=true
MAIL_HOST=localhost
MAIL_PORT=1025
MAIL_USERNAME=dev
MAIL_PASSWORD=dev
MAIL_FROM=no-reply@campuslinkkh.edu.kh
```

Note: Mailpit accepts mail without STARTTLS. `application.yaml` sets
`mail.smtp.starttls.required: true`; if the send is refused, drop STARTTLS for the local
catcher (`MAIL_HOST=localhost` with a profile that relaxes it). Every message, including the
bilingual HTML template, is then visible at http://localhost:8025.

### C. Real Brevo from dev

Use the production-style Brevo settings but point From at a verified sender:

```
MAIL_ENABLED=true
MAIL_HOST=smtp-relay.brevo.com
MAIL_PORT=587
MAIL_USERNAME=8xxxxxx@smtp-brevo.com   # your Brevo SMTP login
MAIL_PASSWORD=your-brevo-smtp-key
MAIL_FROM=no-reply@your-verified-domain.kh   # verified in Brevo, NOT the login
```

---

## 4. Failure behaviour

Even with sending enabled, a send failure is caught and logged (`Failed to send email to
...`) and **not** rethrown — the OTP was already persisted and logged, so the auth flow
continues and the user/dev can still complete verification. `rootCauseMessage(...)` unwraps
the provider's real reply (e.g. Gmail's `535-5.7.8`) instead of Spring's generic wrapper.
