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
| `app.mail.from` | `${MAIL_USERNAME}` (or `MAIL_FROM` when set) | The From header. Standard mailbox setups normally use the authenticated mailbox. |

`app.mail.enabled` defaults to **`false` in the `dev` profile** (`application-dev.yaml`),
so a fresh dev checkout tests OTP flows with no mail server at all.

---

## 2. Standard SMTP configuration

The application uses Spring Boot's standard `JavaMailSender`; there is no provider-specific
mail client in the OTP path. The default production configuration targets Gmail SMTP on port
587 with STARTTLS. `MAIL_FROM` is optional and falls back to `MAIL_USERNAME`, which keeps the
authenticated mailbox and the From header aligned for a normal Gmail account.

For Gmail, use a Google App Password rather than the account password. The account must have
2-Step Verification enabled. If the deployment environment still blocks port 587, standard
SMTP cannot connect until that network restriction is removed or a permitted SMTP submission
port is configured by the mail provider.

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

### C. Real Gmail SMTP from dev

Use the standard Gmail SMTP settings with a Google App Password:

```
MAIL_ENABLED=true
MAIL_HOST=smtp.gmail.com
MAIL_PORT=587
MAIL_USERNAME=your-account@gmail.com
MAIL_PASSWORD=your-16-character-app-password
MAIL_FROM=your-account@gmail.com
```

---

## 4. Failure behaviour

Even with sending enabled, a send failure is caught and logged (`Failed to send email to
...`) and **not** rethrown, so an SMTP outage does not roll back the persisted OTP. In real-send
mode the OTP is never written to logs; the user must request another code after delivery is
restored. `rootCauseMessage(...)` unwraps the SMTP server's reply (for example Gmail's
`535-5.7.8`) instead of only reporting Spring's generic wrapper.

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

## 6. Notification email is a separate transport

Generic notification email is separate from authentication OTP email. It uses the same
configured standard SMTP connection and `app.mail.from` address, but it is controlled by
its own switch:

```text
NOTIFICATION_EMAIL_ENABLED=false
```

When enabled, notification requests that explicitly include the `EMAIL` channel are delivered
through the notification outbox. OTP delivery still uses `MAIL_ENABLED` and `EmailService`; it
does not pass through the notification email transport. Keep notification email disabled in dev
unless an SMTP catcher or a safe test mailbox is configured.

Users can control the channel with the authenticated profile-settings endpoints:
`GET /api/v1/profile/settings/notifications/channels` and
`PUT /api/v1/profile/settings/notifications/channels/EMAIL` with `{ "enabled": false }`.
