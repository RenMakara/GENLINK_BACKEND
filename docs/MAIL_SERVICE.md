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

---

## 5. Template conventions

Four templates, one per (language × purpose): `otp-register-{en,km}.html` and
`forgot-password-{en,km}.html`. `EmailServiceImpl.buildOtpHtml(...)` does a single
`{{otp}}` string replace — there is no template engine, so the placeholder must stay
spelled exactly that way and no other `{{...}}` syntax is available.

Shared layout: centred card on a `#eef2f8` page, round icon badge, headline, body copy,
tinted code panel, expiry line, amber "never share this code" notice, footer band.
Register uses the teal ramp (`#0f766e`), password reset the blue ramp (`#2563eb`).

Constraints these templates are written against:

- **Tables and inline CSS only.** No `<style>` block, no external stylesheet, no
  web fonts — Gmail strips or ignores all three.
- **No images.** The icon badges are HTML entities (`&#9993;` envelope,
  `&#128274;` lock, `&#9888;` warning), so nothing breaks when a client blocks
  remote content. Adding a real logo means CID-embedding via
  `MimeMessageHelper.addInline(...)`, not an `<img src="https://...">`.
- **No JavaScript**, so no copy-to-clipboard button on the code.
- **Khmer text never takes `letter-spacing` or `text-transform`** — spacing breaks the
  consonant/subscript clusters and uppercasing does nothing. The km templates use plain
  bold labels where the en ones use spaced small caps, and taller `line-height`
  (26–28px) to fit descenders. Only the OTP digits are letter-spaced, since they are Latin.
- `border-radius` is ignored by Outlook desktop's Word engine: the round badge and rounded
  card degrade to squares there. Accepted rather than worked around with VML.

Each template also carries a hidden preheader `<div>` — the grey line clients show beside
the subject in the inbox list. It is invisible in the body, so it has to restate the
essentials on its own.

To eyeball a change without sending, substitute the placeholder and open the file:

```bash
sed 's/{{otp}}/582941/g' src/main/resources/templates/email/otp-register-en.html > /tmp/preview.html
```

For a true client-rendered check, use the Mailpit route in §3B.
