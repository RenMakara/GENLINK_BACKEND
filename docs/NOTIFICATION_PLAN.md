# Notification — Domain Build-Out Plan

Status: **Phase 0 done; Task 1A done; Task 1B implementation complete with local Gradle execution blocked** (2026-09-09). The read side is implemented: entities
`Notification` + bilingual `NotificationTemplate`, DB tables via migration 0012, a controller,
repository, service, pagination, unread count, and mark-read operations. The architecture plan
for emission and cross-module integrations is now prepared. Task 1A now provides the internal
emission foundation and initial template seed; no domain trigger is wired yet.
Last verified: 2026-09-09

Scope: **in-app notifications only** — no email/push transport in this plan (that's a future
transport layer behind the same emit API). Cadence: **one phase per turn**, each compiled + tested +
documented, same as the finance work. The app can't boot in this workspace, so migrations are
verified structurally; run them on a dev DB before shipping.

Conventions (same as other domains): UUID PKs, BaseEntity, DTO in/out, `ApiResponse` envelope,
`PageResponse` for lists, owner-scoped lookups return not-found, `CurrentUserProvider` for the user,
bilingual via `LocalizedTextSelector` / `LanguageResolver`, additive migrations.

---

## Architecture decision before implementation

Keep Notification inside the existing backend modular monolith. Do **not** add a separate
microservice, Kafka, or another broker for the first in-app implementation. Other domains must
depend on a small internal notification port, never on `NotificationRepository` or notification
entities directly.

### Planned write flow

```text
Domain service action
  -> domain/application notification event
  -> NotificationEmitter
  -> recipient + language resolution
  -> bilingual template rendering with parameters
  -> rendered notification saved in a new transaction
  -> current Notification API / future Mobile BFF
```

The first implementation will publish the notification event after the source transaction
commits and persist the notification in a separate transaction. A failed notification is logged
and must not roll back the business action that caused it. This keeps the initial design simple
while avoiding a notification insert that makes RSVP, expense creation, or another domain action
fail.

Before push/email delivery or more than one backend instance is introduced, add a durable
`notification_outbox` with retry and deduplication. An in-memory event alone is not sufficient for
those requirements.

### Internal emitter contract

The planned `NotificationEmitter` accepts a recipient user id, a stable template key, a
notification type, placeholder parameters, and optional navigation metadata:

```text
emit(recipientUserId, templateKey, type, params, referenceType?, referenceId?, dedupeKey?)
```

The emitter owns template lookup, Khmer/English selection, placeholder replacement, read-status
defaults, and persistence. It stores the already-rendered title/message and language so the read
side remains fast and does not need to re-localize old messages.

Each notification should carry enough reference information for Flutter to open the related
screen, for example `EVENT + eventId`, `BUDGET + budgetId`, or `TASK + taskId`. A dedupe key is
needed for reminders and scheduled jobs so a retry cannot create repeated rows.

### Boundaries and rules

- Source modules publish facts; they do not format localized text.
- Notification templates are seeded and versioned by Liquibase, not created by frontend input.
- Template keys and notification types are stable API contracts.
- The recipient is always explicit; never infer it from the current request after a queued event.
- Reads remain owner-scoped and another user's notification id returns not-found.
- `notificationEnabled` from profile settings is a later preference gate; it is not silently
  assumed to disable notifications until that phase is implemented and tested.
- The current scope is in-app only. OTP email through Brevo is a separate mail flow, not an
  in-app notification.

### Current integration audit

No domain currently calls `NotificationService` to emit a row. The existing Admin permissions
`notification:send` and `notification:template:manage` are not proof of working Admin endpoints;
Admin notification controllers are still pending. The Mobile BFF route
`/api/v1/mobile/notifications` also remains `NOT_IMPLEMENTED`; Flutter can use the direct
`/api/v1/notifications` read API until the BFF phase lands.

---

## Phase 0 — Make the existing API real + fix the bug — DONE

The read side, done properly. No emitting yet (that's Phase 1); this makes what already exists work.

- [x] Fixed the controller route bug: `@PostMapping("/<built-in function id>/read")` -> `/{id}/read`.
- [x] Real `NotificationService`:
  - `getMine(unreadOnly, pageable)` — owner-scoped, newest-first, optional unread-only, paginated.
  - `markRead(id)` — ownership-checked (another user's id reads as not-found); idempotent; stamps
    `read_at` only on the first read.
  - `markAllRead()` — one `@Modifying` bulk update; returns how many changed.
  - `unreadCount()` — for the bell badge.
- [x] Repo queries: paged by user, paged unread-by-user, `findByIdAndUserId`, unread count, and the
  `@Modifying` bulk mark-all-read.
- [x] Endpoints: `GET /notifications?unreadOnly=&page=&size=`, `GET /notifications/unread-count`,
  `POST /notifications/{id}/read`, `POST /notifications/read-all`.
- [x] Tests: `NotificationServiceImplTest` (7) — list, unread filter, unread count, mark one,
  idempotent re-read, ownership 404, mark-all.

Notes: notifications carry their **already-rendered** `title`/`message` + `language` (rendered at
emit time in Phase 1), so the read side needs no re-localization. No soft delete on notifications.
DB tables already existed (migration 0012), so Phase 0 added no migration.

## Phase 1 — Notification emission foundation

### Task 1A — Emitter, renderer, and template seed (current task)

- [x] Add the internal `NotificationEmitter` port and implementation. The source transaction
  publishes an application event; an after-commit handler persists the notification in a new
  transaction and logs failures without breaking the source action.
- [x] Add the template renderer and repository lookup by `templateKey`. Render `{placeholder}`
  tokens using bounded, explicit parameters and select the user's preferred language.
- [x] Seed the first bilingual templates for the trigger list below. Missing template keys must
  fail safely and be observable in logs/tests; they must not create a blank notification.
- [x] Add unit tests for rendering, placeholder substitution, language selection, missing/unknown
  keys, and after-commit failure isolation.

### Task 1B — Navigation metadata and dedupe (follow-up task)

- [x] Add the next additive notification migration (0053 or the next unused changeset) for
  `reference_type`, `reference_id`, and a nullable recipient-scoped `dedupe_key` if the existing
  schema cannot support navigation and retry-safe inserts. Do not reuse migration 0038.
- [x] Extend `NotificationResponse` with reference/navigation data only after the migration adds
  it.
- [x] Add tests for deduplication and reference metadata.

### Task 1B implementation report — 2026-09-09

- Changed: added optional `referenceType`/`referenceId` and `dedupeKey` to the internal request,
  persisted them on `Notification`, and returned the two navigation fields from the direct read
  API. The writer skips an existing recipient-scoped dedupe key; the database unique partial index
  remains the concurrency guard.
- Database: added Liquibase migration `0053-add-notification-navigation-and-dedupe.sql`, including
  additive columns, a reference lookup index, and a recipient-scoped unique dedupe index.
- API/Postman: added the four direct notification requests to
  `docs/GenLinkKH.postman_collection.json` and documented `referenceType`/`referenceId` in the
  list request. The Mobile BFF route remains a later phase.
- Tests/checks: Postman JSON parsing and `git diff --check` passed. The targeted Gradle test command
  was attempted again, but this environment stops before compilation with
  `Unable to establish loopback connection`; no test failure was observed. The user confirmed the
  previous Task 1A test run passed on their host.
- Deferred: Community/Finance trigger wiring, Mobile BFF, preferences, outbox, push, and email
  remain follow-up tasks. Do not deploy this commit until CI or a dev machine confirms compilation
  and the complete test suite.

### Task 1A implementation report — 2026-09-09

- Changed: added the internal `NotificationEmitter`/`NotificationRequest` contract, application
  event, after-commit listener, and separate `REQUIRES_NEW` writer; added template lookup by key,
  bounded parameter validation, bilingual rendering with English fallback, and unread defaults.
- Database: added Liquibase migration `0052-seed-notification-templates.sql` and registered it in
  the master changelog. It seeds the initial Community, Finance, Productivity, Entertainment,
  and Security bilingual keys with rerun-safe `ON CONFLICT` handling.
- Tests/checks: `git diff --check` passed. The targeted Gradle test command was attempted with the
  wrapper and cached distribution, but both stopped before compilation because this environment
  cannot establish Gradle's loopback connection (`Unable to establish loopback connection`). No
  test failure was observed; the test suite still must run in CI or on a normal development host.
- API/Postman: no external endpoint or response contract changed, so the Postman collection is
  unchanged for this task.
- Deferred: Community/Finance trigger wiring, navigation metadata, deduplication, Mobile BFF,
  preferences, outbox, push, and email remain follow-up tasks. Do not deploy this commit until
  CI or a dev machine confirms compilation and tests.

## Phase 2 — Community triggers

- [ ] New event RSVP → notify the event organiser, including event reference and attendee name.
- [ ] Private community join request → notify the owner/admin; approval → notify the requester.
- [ ] Post/discussion moderation action → notify the affected author when the product contract
  defines a user-visible action.
- [ ] Event reminder → notify attendees through a scheduler only after reminder timing and
  dedupe rules are agreed.
- [ ] Tests prove recipient selection, idempotency, and that a notification failure does not undo
  RSVP, join, or moderation state.

## Phase 3 — Finance triggers

- [ ] Budget approaching limit and budget exceeded. Decide whether the trigger is expense-create,
  a daily scheduler, or both; dedupe repeated alerts.
- [ ] Saving goal reached.
- [ ] Auto-save applied and auto-save skipped for insufficient funds.
- [ ] Simulated event/payment receipt where the user should see a ledger confirmation.
- [ ] Tests for USD/KHR context where a financial notification includes an amount or currency.

## Phase 4 — Productivity, Entertainment, Security, and Admin triggers

- [ ] Productivity task due/reminder, habit streak, and goal milestone.
- [ ] Entertainment challenge completed and reward earned.
- [ ] Security suspicious-login notification, only after the security event contract and privacy
  policy are approved.
- [ ] Admin broadcast and notification-template management only after Admin controllers,
  permission checks, audit records, and recipient limits exist. The existing Admin permission
  constants alone do not satisfy this phase.

## Phase 5 — Mobile BFF and client contract

- [ ] Fill `/api/v1/mobile/notifications` with unread count and recent notifications by calling
  the notification service, not the notification controller.
- [ ] Keep the direct `/api/v1/notifications` endpoints for pagination and mark-read operations.
- [ ] Update the Postman collection and feature docs with the final response/reference contract.

## Phase 6 — Preferences and delivery expansion

- [ ] Add per-type on/off and quiet hours; the emitter must enforce them for eligible notifications.
- [ ] Add `notification_outbox`, retry, dedupe, and delivery status before external transports or
  multiple backend instances.
- [ ] Add push/email adapters behind the same internal emit contract. Brevo remains a transport
  implementation for email and must not be mixed with the OTP flow.

---

## Definition of done and reporting for every implementation phase

Before coding a phase, this plan must be approved/updated with its scope and trigger decisions.
After coding, the same phase must be marked `DONE` only after the following are recorded:

- What backend code, migration, endpoint, template, and module integration changed.
- What tests/checks were run and their result.
- Any test, environment, migration, or deployment failure, including whether it blocked release.
- What remains intentionally deferred and the next phase.
- Related feature docs and the Postman collection status.

Per the standing rule, `PROJECT_STATUS`, this plan, a feature doc, and the Postman collection are
updated as each phase lands (backend only). For Task 1A, this plan is the feature/status document;
the Postman collection remains unchanged because the public API contract did not change.
