# Notification — Domain Build-Out Plan

Status: **Phase 0 done; Tasks 1A/1B, 2A-2H, 3A-3D, 4A-4F, Phase 5, and Phase 6 Tasks 6A-6D complete; the user confirmed `gradlew test` build success for Tasks 4B-4D, Task 4F, Phase 5, and Tasks 6A-6D, while local Codex Gradle execution remains blocked** (2026-09-10). The read side is implemented: entities
`Notification` + bilingual `NotificationTemplate`, DB tables via migration 0012, a controller,
repository, service, pagination, unread count, and mark-read operations. The architecture plan
for emission and cross-module integrations is now prepared. Task 1A provides the internal
emission foundation and initial template seed; Community Tasks 2A-2G now wire event RSVP,
private join-request, moderation, and scheduled event-reminder triggers. Finance Tasks 3A-3D now
wire budget threshold, saving-goal completion, auto-save run, and payment receipt alerts to finance mutations.
Productivity Tasks 4A-4C now wire configured task reminders, completed habit streak updates, and
goal milestone completion to the same in-app notification path. Entertainment Task 4D now wires
claimed challenge rewards to that path as well. Security Task 4E now alerts on unfamiliar login
context, and Admin Task 4F now provides audited broadcast and `ADMIN_` template management.
Last verified: 2026-09-10

Scope: **in-app notifications plus opt-in email and online push adapters** behind the same internal
contract. SMTP/Brevo email is separate from OTP; the first push adapter is the existing authenticated
WebSocket channel and does not provide offline FCM/APNs delivery. Cadence: **one phase per turn**,
each compiled + tested + documented, same as the finance work. The app can't boot in this workspace,
so migrations are verified structurally; run them on a dev DB before shipping.

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
  -> delivery-policy gate
  -> after-commit durable notification_outbox row per selected channel
  -> locked outbox worker + retry/backoff
  -> recipient + language resolution
  -> bilingual template rendering with parameters
  -> rendered notification saved in a new transaction
  -> current Notification API / future Mobile BFF
```

The source action still publishes the notification event after commit. The listener now stores the
request facts durably and independently, and a bounded worker later renders and writes the in-app
row. A failed enqueue or delivery is logged/retried and must not roll back the business action
that caused it. The after-commit handoff intentionally retains a short process-crash gap; an
atomic same-transaction outbox is a later opt-in migration for source modules.

Before push/email delivery or more than one backend instance is introduced, the durable
`notification_outbox` with retry and deduplication must be enabled. Task 6B now provides that
foundation; an in-memory event alone is not sufficient for those requirements.

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
- Domain-trigger templates are seeded and versioned by Liquibase. Admin broadcast templates are
  the explicit exception: they are created/updated only through the audited Admin API in the
  reserved `ADMIN_` namespace.
- Template keys and notification types are stable API contracts.
- The recipient is always explicit; never infer it from the current request after a queued event.
- Reads remain owner-scoped and another user's notification id returns not-found.
- Global `notificationEnabled`, per-type overrides, and quiet hours are enforced by the emitter;
  a preference storage failure fails open so it cannot roll back the source business action.
- The default channel remains in-app. Email and online push are explicit opt-in transport channels;
  OTP email through Brevo is a separate auth flow, not a notification transport.

### Current integration audit

Community, Finance, Productivity, Entertainment, Security, and Admin broadcast services now use
the notification application port; domain/admin actions emit rows after their source
transactions, while the BFF reads through `NotificationService`. Admin notification endpoints
enforce `notification:send` and `notification:template:manage`, record success/failure audit
rows, and cap fan-out at 500 active recipients. The Mobile BFF route
`/api/v1/mobile/notifications` now returns the unread badge and a bounded recent preview; the
direct `/api/v1/notifications` route remains the full pagination and mark-read API.

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

- [x] New event RSVP → notify the event organiser after a successful non-idempotent RSVP,
  including `EVENT + eventId`, attendee name, and a recipient-scoped dedupe key. Organiser
  self-RSVPs and idempotent re-taps do not emit a row.
- [x] Private community join request → notify every active `OWNER`/`ADMIN` after a new or reopened
  pending request, including `COMMUNITY + communityId`, requester name, and a recipient-scoped
  dedupe key.
- [x] Join-request approval → notify the requester after `PENDING → ACTIVE`, including
  `COMMUNITY + communityId` and a recipient-scoped dedupe key.
- [x] Community post removal by a moderator → notify the affected author with `POST + postId`,
  community name, moderation action, and a recipient-scoped dedupe key.
- [x] Forum discussion moderation → notify the affected author after an app admin removes the
  discussion, including `DISCUSSION + discussionId`, discussion title, moderation action, and a
  recipient-scoped dedupe key.
- [x] Event reminder → every five minutes, notify `JOINED` attendees when a published event is
  roughly 24 hours away, with `EVENT + eventId`, formatted start time, and a recipient-scoped
  dedupe key.
- [x] Tests prove recipient selection, idempotency, and that a notification failure does not undo
  RSVP, join, or moderation state.

### Task 2A implementation report — 2026-09-09

- Changed: `EventServiceImpl` now emits `COMMUNITY_EVENT_RSVP_ORGANIZER` only after a new RSVP
  has reserved a seat, completed payment when required, saved the RSVP, and awarded XP. The
  organiser receives the attendee display name, `EVENT` navigation reference, and a stable
  `event-rsvp:{eventId}:{attendeeId}` dedupe key. Repeated joined requests and organiser self-RSVPs
  stay silent.
- Tests/checks: added Community service coverage for recipient selection, reference data, dedupe
  key construction, and idempotent RSVP silence. `git diff --check` passed. The user confirmed
  the previous Gradle test run passed; this environment could not execute the new Gradle run because
  the wrapper stops before compilation with `Unable to establish loopback connection`.
- API/Postman: no endpoint shape changed, so the collection requires no new request for this task.
- Deferred: moderation, reminders, and the remaining Community trigger tests remain for later
  tasks. Do not deploy until CI or a dev machine runs the full suite for this commit.

### Task 2B implementation report — 2026-09-09

- Changed: `CommunityServiceImpl` now emits `COMMUNITY_JOIN_REQUEST_OWNER` after saving a new or
  reopened `PENDING` request for a PRIVATE community. The owner receives the requester name,
  `COMMUNITY + communityId` navigation data, and a stable
  `community-join-request:{communityId}:{requesterId}` dedupe key.
- Behavior: existing pending requests, public joins, active joins, and owner self-requests stay
  silent. Notification persistence remains after-commit and isolated from the membership action
  through the notification foundation.
- Tests/checks: added coverage for owner recipient selection, parameters, navigation metadata,
  deduplication data, and silence for public/repeated pending joins. `git diff --check` is required
  before commit. The targeted Gradle run was attempted, but this workspace stops before compilation
  because it cannot establish Gradle's loopback connection; the user confirmed the previous Gradle
  test run passed on their host.
- API/Postman: no endpoint or response shape changed, so the Postman collection is unchanged for
  this task.
- Deferred: moderation, reminders, durable outbox, and external push/email transports.

### Task 2C implementation report — 2026-09-09

- Changed: `CommunityServiceImpl.approveJoinRequest` now emits
  `COMMUNITY_JOIN_REQUEST_ACCEPTED` after the pending member is activated, `joinedAt` is stamped,
  and the community member count is incremented. The requester receives the community name,
  `COMMUNITY + communityId` navigation data, and a stable
  `community-join-request-accepted:{communityId}:{requesterId}` dedupe key.
- Behavior: only a valid `PENDING` request can be approved, so repeated approval attempts remain
  not-found and do not emit another notification. Notification persistence remains after-commit
  and isolated from the membership action through the notification foundation.
- Tests/checks: added coverage for requester selection, template/type, parameters, navigation
  metadata, deduplication data, and no emission for non-pending approval. The user confirmed
  `gradlew test` passed for the previous task; this task's targeted run must also be confirmed on
  a normal development host because this workspace cannot establish Gradle's loopback connection.
- API/Postman: no endpoint or response shape changed, so the Postman collection is unchanged for
  this task.
- Deferred: moderation, reminders, durable outbox, and external push/email transports.

### Task 2D implementation report — 2026-09-09

- Changed: private join requests now query active community manager memberships and emit one
  `COMMUNITY_JOIN_REQUEST_OWNER` notification per `OWNER`/`ADMIN`. Recipient-scoped deduplication
  keeps the same request from creating repeated rows for each manager, and the lookup follows
  current roles so ownership transfers are handled correctly.
- Behavior: public joins and repeated pending requests remain silent. Null/duplicate manager
  records are skipped safely; the membership request is still the source transaction and the
  notification writer remains after-commit.
- Tests/checks: added coverage for owner/admin recipient fan-out and one notification per manager.
  `git diff --check` is required before commit. The targeted Gradle run is blocked in this workspace
  before compilation by `Unable to establish loopback connection`; the user confirmed the prior
  `gradlew test` run passed on their host.
- API/Postman: no endpoint or response shape changed, so the Postman collection is unchanged for
  this task.
- Deferred: moderation, reminders, durable outbox, and external push/email transports.

### Task 2E implementation report — 2026-09-09

- Changed: `PostServiceImpl.removeAsModerator` now emits `COMMUNITY_POST_MODERATED` after a
  moderator successfully soft-deletes a community post, decrements the community post count,
  and clears its hashtags. The author receives `communityName`, `moderationAction=removed`, a
  `POST + postId` navigation reference, and a stable
  `community-post-moderated:{postId}:{authorId}` dedupe key.
- Behavior: author-only deletion, report dismissal, missing posts, non-community posts, and
  unauthorized moderation do not emit this notification. The source moderation transaction
  remains separate from after-commit notification persistence.
- Tests/checks: added coverage for author recipient selection, template/type, parameters,
  navigation metadata, deduplication data, and no emission when moderation is refused. The
  targeted Gradle run is blocked in this workspace before compilation by
  `Unable to establish loopback connection`; the user confirmed the prior `gradlew test` run
  passed on their host.
- API/Postman: no endpoint or response shape changed, so the Postman collection is unchanged for
  this task.
- Deferred: forum discussion moderation notification, reminders, durable outbox, and external
  push/email transports.

### Task 2F implementation report — 2026-09-09

- Changed: `DiscussionServiceImpl.removeAsAdmin` now emits `FORUM_DISCUSSION_MODERATED` after an
  app admin successfully soft-deletes a forum discussion. The author receives the discussion
  title, `moderationAction=removed`, a `DISCUSSION + discussionId` navigation reference, and a
  stable `forum-discussion-moderated:{discussionId}:{authorId}` dedupe key. Anonymous discussions
  still notify the stored author; anonymity remains a public response rule.
- Behavior: author-only deletion, report dismissal, missing discussions, and incomplete author
  data do not emit this notification. Notification persistence remains after-commit and isolated
  from the source moderation transaction.
- Data: added Liquibase migration `0054-seed-forum-notification-template.sql` with the bilingual,
  rerun-safe `FORUM_DISCUSSION_MODERATED` template and registered it in the master changelog.
- Tests/checks: added coverage for recipient selection, template/type, parameters, navigation
  metadata, deduplication data, no emission for missing/dismissed/self-delete paths, and template
  migration structure. The targeted Gradle run remains blocked in this workspace before
  compilation by `Unable to establish loopback connection`; the user confirmed `gradlew test`
  passes on their host.
- API/Postman: no endpoint or response shape changed, so the Postman collection is unchanged for
  this task.
- Deferred: event reminders, durable outbox, and external push/email transports.

### Task 2G implementation report — 2026-09-09

- Changed: `EventReminderServiceImpl` queries joined attendees for non-deleted, published events
  entering a 24-hour reminder window (five minutes on either side of the lead time) and emits
  `COMMUNITY_EVENT_REMINDER` with the event title, formatted start time, `EVENT + eventId`, and a
  stable `event-reminder-24h:{eventId}:{attendeeId}` dedupe key.
- Scheduler: added `EventReminderJob`, running every five minutes in `Asia/Phnom_Penh`, with the
  cron configurable through `EVENT_REMINDER_CRON`; the existing `app.scheduling.enabled=false`
  switch disables it with the other scheduled jobs. Job failures are logged and do not stop later
  runs; notification persistence remains after-commit.
- Behavior: only `JOINED` attendees are eligible. Cancelled/interested RSVPs, draft/cancelled/
  deleted events, incomplete rows, and repeated window scans do not create duplicate notification
  rows. The database-backed dedupe index remains the concurrency guard.
- Data: added Liquibase migration `0055-seed-event-reminder-template.sql` with a bilingual,
  rerun-safe template and registered it in the master changelog.
- Tests/checks: added coverage for the 24-hour query window, attendee selection, rendered
  parameters, navigation metadata, dedupe key, incomplete-row silence, scheduler delegation,
  failure isolation, cron, timezone, and migration structure. The targeted Gradle run remains
  blocked in this workspace before compilation by `Unable to establish loopback connection`; the
  user confirmed `gradlew test` passes on their host.
- API/Postman: no endpoint or response shape changed, so the Postman collection is unchanged for
  this task.
- Limitation: this is an in-app, best-effort scheduled reminder. A process outage that spans the
  five-minute window can miss a reminder; a durable outbox/claim table and distributed scheduler
  lock remain production-scale follow-ups.

### Task 2H implementation report — 2026-09-09

- Changed: completed the Community notification reliability test matrix for RSVP, private join
  requests, post moderation, and forum discussion moderation. Each test drives the existing
  after-commit listener with a failing notification writer and verifies that the source state is
  still persisted or returned successfully.
- Coverage: the existing Community tests already prove recipient selection, navigation metadata,
  recipient-scoped dedupe keys, idempotent RSVP/join behavior, and reminder failure isolation. The
  new cases verify writer failure does not undo RSVP state, a pending join request, post soft
  deletion/count cleanup, or discussion soft deletion.
- Architecture: no domain or API contract changed. The shared `NotificationEventListener` remains
  responsible for catching/logging writer failures after the source transaction, so notification
  persistence stays secondary to the Community action.
- Tests/checks: `git diff --check` passed. The targeted Gradle run was attempted locally but
  stopped before compilation with `Unable to establish loopback connection`; no assertion failure
  was observed. Run full `gradlew test` on the development host/CI for this commit.
- API/Postman: no endpoint, response shape, migration, or Postman request changed.

## Phase 3 — Finance triggers

- [x] Budget approaching limit and budget exceeded. The chosen trigger is expense create/update:
  live same-currency spend is evaluated after a successful mutation, and recipient-scoped dedupe
  keys prevent repeated approaching/exceeded rows for the same budget.
- [x] Saving goal reached from a manual deposit, target update, or auto-save deposit. Only the
  transition into `COMPLETED` emits the owner notification, with a stable goal-scoped dedupe key.
- [x] Auto-save applied and auto-save skipped for insufficient funds. Each due run emits an
  owner-scoped notification with `AUTO_SAVE_RULE` navigation metadata and a rule/run-date dedupe key.
- [x] Simulated event/payment receipt where the user should see a ledger confirmation. Paid event
  RSVP receipts point to the event; wallet payment receipts point to the ledger transaction.
- [x] Tests for USD/KHR context where a budget notification includes the currency in its rendered
  budget name and queries spend from the matching currency only.

### Task 3A implementation report — 2026-09-09

- Changed: added `BudgetAlertService` and `BudgetAlertServiceImpl`. Expense create/update now
  capture the budget status before mutation and evaluate live spend after the wallet/expense
  operation succeeds. Alerts are emitted only when a budget crosses SAFE → WARNING (at 80%) or
  WARNING → LIMIT_REACHED (at 100%); deletions and repeated spends do not emit alerts.
- Currency and navigation: budgets are matched by user, month, year, and currency, so USD and KHR
  spend never mix. The notification recipient is the budget owner and each row carries
  `BUDGET + budgetId` plus a stable alert-specific dedupe key.
- Data/API: reused the bilingual templates from migration `0052`; no new migration, endpoint,
  response contract, or Postman request was required.
- Tests/checks: added budget threshold and expense integration unit coverage. `git diff --check`
  passed. The targeted Gradle command was attempted but this workspace stops before compilation
  with `Unable to establish loopback connection`; no assertion failure was observed. Run
  `gradlew test` on the development host/CI before release.
- Deferred: saving-goal, auto-save, and payment-receipt notification triggers remained in the rest
  of Phase 3 at this checkpoint. Durable outbox/retry and external push/email delivery remain
  later phases.

### Task 3B implementation report — 2026-09-09

- Changed: added the shared `SavingGoalCompletionNotifier` port and implementation. Manual saving
  deposits, saving-goal target updates, and auto-save deposits now notify the goal owner when the
  goal transitions from an unfinished state to `COMPLETED`.
- Notification data: reused `FINANCE_SAVING_GOAL_REACHED` from migration `0052`, with
  `SAVING_GOAL + goalId` navigation metadata and the stable
  `saving-goal-reached:{goalId}` dedupe key. Notification persistence remains after-commit and
  isolated from the saving/wallet transaction.
- Behavior: cancelled goals, already-completed goals, insufficient-funds auto-save runs, and
  incomplete goal data do not emit a completion row. A goal can still reopen when its target is
  raised, but the stable dedupe key prevents duplicate completion rows for the same goal.
- Data/API: no migration, endpoint, response contract, or Postman request was required.
- Tests/checks: added notifier rendering/metadata coverage and integration assertions for manual
  deposit and auto-save completion. Run `gradlew test` on the development host/CI before release.
- Deferred: payment-receipt notifications remain in Phase 3.

### Task 3C implementation report — 2026-09-09

- Changed: `AutoSaveServiceImpl` now emits an applied notification after the wallet debit, saving
  deposit, and goal update succeed. A positive run that cannot be funded emits a skipped
  notification and still advances the rule to its next monthly run; the rule remains active.
- Notification data: added bilingual templates in migration `0056` for applied and insufficient-funds
  skipped outcomes. Both use type `FINANCE_AUTO_SAVE`, `AUTO_SAVE_RULE + ruleId` metadata, and
  `amount`, `currency`, and `goalName` parameters. Dedupe keys include the rule id and scheduled
  run date, so scheduler retries are safe without suppressing later months.
- Behavior: zero-amount runs, completed/cancelled/deleted goals, and incomplete rule/goal details
  do not emit auto-save outcome notifications. Existing saving-goal completion notifications remain
  separate and still use their goal-scoped dedupe key.
- Data/API: registered `0056-seed-auto-save-notification-templates.sql`; no endpoint, response
  contract, or Postman request changed.
- Tests/checks: added applied/skipped/zero-amount notification assertions and migration-template
  coverage. Run `gradlew test` on the development host/CI before release; this workspace's local
  Gradle execution remains blocked before compilation by its loopback restriction.
- Deferred: durable outbox/retry and external push/email delivery remain later work.

### Task 3D implementation report — 2026-09-09

- Changed: paid event RSVP now emits a receipt to the attendee after the ledger charge, RSVP save,
  and XP award succeed. Wallet `pay` now emits a receipt after the provider-approved KHQR debit
  creates a ledger transaction.
- Notification data: added bilingual migration `0057` templates for event and wallet payment
  receipts. Event receipts use `COMMUNITY_EVENT_PAYMENT`, `EVENT + eventId`, and a ledger-transaction
  dedupe key. Wallet receipts use `FINANCE_WALLET_PAYMENT`, `TRANSACTION + transactionId`, and the
  same transaction-scoped dedupe strategy. Amount, currency, event/merchant, provider, and provider
  reference are rendered from domain facts.
- Behavior: free RSVP, provider declines, insufficient wallet balance, rolled-back RSVP/payment,
  and incomplete ledger/payment details do not emit a success receipt. The existing organiser RSVP
  notification remains separate, and cancelling an RSVP still has no refund receipt.
- Data/API: registered `0057-seed-payment-receipt-notification-templates.sql`; no endpoint or
  response shape changed, so the Postman collection is unchanged.
- Tests/checks: added event-payment and wallet-payment notification metadata assertions plus bilingual
  migration coverage. Run `gradlew test` on the development host/CI before release; this workspace's
  local Gradle execution remains blocked before compilation by its loopback restriction.
- Deferred: real payment-provider integration, refund handling, durable outbox/retry, and external
  push/email delivery remain out of scope.

## Phase 4 — Productivity, Entertainment, Security, and Admin triggers

- [x] Productivity task reminder: every five minutes, scan incomplete, non-deleted tasks with a
  configured `reminderTime` in the five-minute scheduler window and emit a deduplicated
  `PRODUCTIVITY_TASK_DUE` notification with `TASK + taskId` navigation metadata.
- [x] Productivity habit streak: a successful incomplete → completed habit check-in emits the
  current streak count with `HABIT + habitId` navigation metadata and a check-in-date dedupe key.
- [x] Productivity goal milestone: a successful incomplete → completed milestone update emits
  the goal owner’s progress and uses `GOAL + goalId` navigation metadata.
- [x] Entertainment challenge completed and reward earned: a successful reward claim emits one
  notification after XP award and `CLAIMED` persistence, with `CHALLENGE + challengeId` metadata.
- [x] Security suspicious-login notification: after a prior successful login with complete context,
  a successful password or Google login from an unseen IP + User-Agent emits one in-app alert with
  `SECURITY + userId` navigation metadata. First-ever logins and failed logins stay silent.
- [x] Task 4F — Admin broadcast and notification-template management. The existing Admin
  permission constants alone do not satisfy this phase: the API must enforce permissions,
  write audit records, and cap the recipient population.

### Task 4F contract — Admin broadcast and managed templates

- Broadcast: `POST /api/v1/admin/notifications/broadcast` requires
  `PERM_notification:send`. The request selects an existing `ADMIN_...` template, supplies
  bounded placeholder parameters, and targets either `ALL_ACTIVE` users or an explicit set of
  active user ids. The hard server-side recipient limit is 500; a selected audience containing a
  missing, inactive, or deleted user is rejected rather than partially sent.
- Template management: `GET`, `POST`, and `PUT` under
  `/api/v1/admin/notifications/templates` require
  `PERM_notification:template:manage`. Keys are immutable, must match the `ADMIN_` namespace,
  and cannot be deleted. Domain-trigger templates remain Liquibase-owned so an admin broadcast
  cannot silently change RSVP, payment, moderation, or security wording.
- Safety and consistency: the service pre-validates both localized render paths before publishing
  one after-commit notification event per recipient. Each broadcast gets a batch id and a
  recipient-scoped dedupe key; writer failures remain isolated from the admin request's source
  transaction. The response reports accepted recipients, not external delivery.
- Audit: successful template creates/updates and broadcasts record action, reason, target and
  batch metadata through `AdminAuditService`; validation and recipient-selection failures are
  recorded in a separate transaction before the API error is returned.
- Out of scope: editing seeded module templates, direct notification-row writes, external
  push/email delivery, scheduling, and unlimited or partially accepted broadcasts.

### Task 4A implementation report — 2026-09-09

- Changed: added `TaskReminderService`/`TaskReminderServiceImpl`, a repository query for live
  incomplete tasks whose configured `reminderTime` falls within the scheduler window, and
  `TaskReminderJob` running every five minutes in `Asia/Phnom_Penh`. The cron is configurable with
  `TASK_REMINDER_CRON`; the existing `app.scheduling.enabled=false` switch disables it globally.
- Trigger behavior: only tasks with an explicit `reminderTime` are eligible. Tasks without one do
  not receive an automatic due-date reminder yet. The displayed `{dueAt}` uses `dueDate` when
  present and falls back to `reminderTime` for reminder-only tasks. Completed, soft-deleted, or
  incomplete task rows are skipped.
- Notification data: reused the bilingual `PRODUCTIVITY_TASK_DUE` template from migration `0052`;
  emitted rows use type `PRODUCTIVITY_TASK`, `TASK + taskId` navigation metadata, task title and
  formatted due time parameters, and the stable
  `task-reminder:{taskId}:{reminderTime}` dedupe key. Overlapping scans are therefore safe.
- Failure isolation: a malformed task or emitter failure is logged per row; it does not stop the
  rest of the sweep or affect the task domain transaction. Notification persistence remains
  after-commit through the shared emitter.
- Tests/checks: added service coverage for the query window, rendered parameters, fallback time,
  navigation metadata, deduplication, invalid-row silence, and per-row failure isolation, plus
  scheduler delegation/failure/cron/timezone coverage. `git diff --check` passed. The targeted
  Gradle run remains blocked in this workspace before compilation by `Unable to establish loopback
  connection`; no assertion failure was observed.
- API/Postman: no endpoint, response shape, migration, or Postman request changed. The existing
  task create/update contract already stores `reminderTime`.
- Deferred: habit streak and goal milestone notifications, durable outbox/retry, distributed
  scheduler locking, and external push/email delivery remain later work.

### Task 4B implementation report — 2026-09-09

- Changed: added `HabitStreakNotifier`/`HabitStreakNotifierImpl` and connected it to the habit
  check-in flow. After the check-in is saved and any XP transition succeeds, the service computes
  the live streak from `habit_check_ins` and emits one update for an incomplete → completed
  transition.
- Notification data: reused the bilingual `PRODUCTIVITY_HABIT_STREAK` template from migration
  `0052`; emitted rows use type `PRODUCTIVITY_HABIT`, `HABIT + habitId` navigation metadata,
  `habitName` and `streakDays` parameters, and the stable
  `habit-streak:{habitId}:{checkInDate}` dedupe key.
- Behavior: repeated completion of the same day and undo requests do not emit another update.
  Invalid habit data and a zero/non-current streak are skipped. Notification persistence remains
  after-commit through the shared emitter, so it is secondary to the check-in transaction.
- Tests/checks: added notifier rendering/metadata/silence coverage and habit check-in assertions
  for first completion versus an already completed day. `git diff --check` is required before
  commit and passed. The targeted Gradle run was attempted twice but this workspace stops before
  compilation with `Unable to establish loopback connection`; no assertion failure was observed.
  The user subsequently confirmed the full `gradlew test` build passed on the development host.
- API/Postman: no endpoint, response shape, migration, or Postman request changed. The existing
  `POST /api/v1/productivity/habits/{id}/check-in` contract is reused.
- Deferred: goal milestone notifications, durable outbox/retry, distributed scheduler locking,
  and external push/email delivery remain later work.

### Task 4C implementation report — 2026-09-09

- Changed: added `GoalMilestoneNotifier`/`GoalMilestoneNotifierImpl` and connected it to
  `GoalServiceImpl.setMilestoneCompleted`. After a milestone is saved and the goal progress/status
  is synchronized, the notifier runs only for an incomplete → completed transition.
- Notification data: added bilingual template `PRODUCTIVITY_GOAL_MILESTONE` in Liquibase migration
  `0058`; emitted rows use type `PRODUCTIVITY_GOAL`, `GOAL + goalId` navigation metadata,
  `goalTitle`, `milestoneTitle`, and `progressPercent` parameters, with the stable
  `goal-milestone:{milestoneId}` dedupe key.
- Behavior: completing the last milestone also reports the resulting 100% goal progress. Repeating
  an already completed milestone, reopening it, incomplete data, and invalid progress do not emit
  another completion notification. Notification persistence remains after-commit through the
  shared emitter.
- Data/API: registered migration `0058-seed-goal-milestone-notification-template.sql`; no endpoint
  or response shape changed. The existing milestone completion endpoint and Postman collection are
  unchanged.
- Tests/checks: added notifier rendering/metadata/silence coverage, goal service transition
  assertions, and bilingual migration structure coverage. `git diff --check` passed. The targeted
  Gradle run was attempted, but this workspace stopped before compilation with
  `Unable to establish loopback connection`; no assertion failure was observed. The user
  subsequently confirmed the full `gradlew test` build passed on the development host.
- Deferred: entertainment challenge rewards, security alerts, admin broadcast/template management,
  durable outbox/retry, distributed scheduler locking, and external push/email delivery remain
  later work.

### Task 4D implementation report — 2026-09-09

- Changed: added `ChallengeRewardNotifier`/`ChallengeRewardNotifierImpl` and connected it to
  `ChallengeServiceImpl.claim`. The notifier runs after the reward pipeline succeeds, the user's
  challenge is moved to `CLAIMED`, and the response is rendered.
- Trigger behavior: `complete` alone does not emit because it has not awarded XP yet. A claim that
  is already `CLAIMED`, not yet `COMPLETED`, or not joined is rejected by the existing guards and
  remains silent. The challenge display name follows the resolved language used by the claim
  response, while Notification still selects the localized template for the user.
- Notification data: reused the bilingual `ENTERTAINMENT_CHALLENGE_COMPLETE` template from
  migration `0052`; emitted rows use type `ENTERTAINMENT_CHALLENGE`, `CHALLENGE + challengeId`
  navigation metadata, `challengeName` and `rewardXp` parameters, and the recipient-scoped stable
  `challenge-reward:{challengeId}` dedupe key.
- Behavior: notification persistence remains after-commit through the shared emitter, so a writer
  failure is secondary to the successful reward claim. Invalid challenge/user/reward details are
  skipped safely.
- Tests/checks: added claim integration coverage, notifier rendering/metadata/silence coverage,
  and stronger seed-template placeholder assertions. `git diff --check` passed. The targeted
  Gradle run was attempted twice, but this workspace stopped before compilation with
  `Unable to establish loopback connection`; no assertion failure was observed. Run full
  `gradlew test` on the development host/CI before releasing this commit.
- API/Postman: no endpoint, response shape, migration, or Postman request changed. Existing
  `POST /api/v1/entertainment/challenges/{id}/claim` is reused.
- Deferred: security alerts, admin broadcast/template management, durable outbox/retry,
  distributed scheduler locking, and external push/email delivery remain later work.

### Task 4E implementation report — 2026-09-09

- Decision/contract: a suspicious-login alert is emitted only after a successful password or
  Google login when the account already has at least one successful login with complete IP and
  User-Agent metadata, and the current pair has never been seen. First-ever logins, failed
  logins, incomplete metadata, and legacy history without a comparable fingerprint stay silent.
- Changed: added `SuspiciousLoginNotifier` and connected it to both mobile login paths. Google
  login now records the same `LOGIN_SUCCESS` audit event and stores request metadata on its
  refresh session, so both providers share one detection baseline.
- Notification data: reused the bilingual `SECURITY_SUSPICIOUS_LOGIN` template from migration
  `0052`; emitted rows use type `SECURITY_SUSPICIOUS_LOGIN`, `SECURITY + userId` navigation data,
  the IP-only `loginLocation` parameter, and a SHA-256 fingerprint dedupe key. The User-Agent
  remains in the private security audit/activity data rather than the notification message.
- Privacy/failure behavior: no geolocation is inferred or stored by this feature. Audit lookup,
  event publication, and notification persistence failures are caught/logged so a valid login
  is never rejected because an alert could not be produced.
- Tests/checks: added baseline/known-context/new-context/incomplete-context/failure coverage,
  password and Google login integration assertions, and a template placeholder assertion.
  `git diff --check` passed. Run full `gradlew test` on the development host/CI for this commit.
- API/Postman: no endpoint or response shape changed; the existing `/api/v1/auth/login` and
  `/api/v1/auth/google` contracts are reused. No migration was needed because `0052` already
  seeds the bilingual template.
- Deferred: admin broadcast/template management, durable outbox/retry, preferences/quiet hours,
  and push/email delivery remain later work.

### Task 4F implementation report — 2026-09-10

- Changed: added `AdminNotificationController` and `AdminNotificationService` for listing,
  creating, and updating broadcast-only templates, plus `POST /api/v1/admin/notifications/broadcast`.
  Template keys are immutable and restricted to the reserved `ADMIN_` namespace; seeded
  module-trigger templates remain Liquibase-owned and are not exposed for runtime edits.
- Safety and recipient behavior: broadcasts require the `notification:send` permission and an
  audit reason, pre-validate both English and Khmer rendering paths, accept either all active
  users or an explicit list, reject missing/inactive/deleted/duplicate recipients without a
  partial send, and enforce a hard maximum of 500 recipients. One after-commit emitter request
  is published per recipient with a batch-scoped dedupe key; the response reports accepted
  recipients, not external delivery.
- Audit behavior: template creates/updates and successful broadcasts use `AdminAuditService`
  with target/batch metadata. Validation, template, recipient, and persistence failures use
  independent failure audit rows before the API error is returned.
- API/Postman: added the three template endpoints and broadcast examples to
  `docs/GenLinkKH.postman_collection.json`; no external push/email transport or mobile BFF
  response was changed.
- Tests/checks: added service coverage for selected fan-out, the 500-recipient limit, inactive
  recipient rejection, namespace enforcement, bilingual placeholder validation, and audit
  calls. Postman JSON parsing and `git diff --check` passed. The targeted Gradle run was
  attempted with the wrapper and elevated network access, but this workspace stopped before
  compilation with `Unable to establish loopback connection`; no assertion failure was observed.
  Run full `gradlew test` on the development host/CI before release.
- Deferred: Mobile BFF, preference/quiet-hour enforcement, durable outbox/retry, and external
  push/email delivery remain later phases.

## Phase 5 — Mobile BFF and client contract — DONE

- [x] Fill `/api/v1/mobile/notifications` with unread count and recent notifications by calling
  the notification service, not the notification controller.
- [x] Keep the direct `/api/v1/notifications` endpoints for pagination and mark-read operations.
- [x] Update the Postman collection and feature docs with the final response/reference contract.

### Phase 5 implementation report — 2026-09-10

- Changed: replaced the `NOT_IMPLEMENTED` mobile route with `MobileNotificationBffService` and
  `MobileNotificationBffServiceImpl`. `GET /api/v1/mobile/notifications?limit=10` now returns
  `unreadCount`, newest-first `recentNotifications`, and `hasMore`. The preview limit is bounded
  to 1-20; the default is 10.
- Boundary: the BFF calls `NotificationService.getMine` and `NotificationService.unreadCount`;
  it does not call the notification controller or repositories directly. Stored rendered text,
  language, type, and reference metadata are passed through unchanged for Flutter navigation.
- API/Postman: retained the direct `/api/v1/notifications` list/unread/read/read-all endpoints
  for full pagination and state changes, and documented the BFF query/response contract in
  `docs/GenLinkKH.postman_collection.json`.
- Tests/checks: added BFF service coverage for unread/recent mapping, newest-page sizing and
  invalid limits, plus controller delegation coverage. Postman JSON parsing and `git diff --check`
  passed. The user subsequently confirmed full `gradlew test` passed on the development host;
  this workspace's local Gradle execution remains blocked before compilation by its loopback
  restriction.
- Deferred: preferences/quiet hours, durable outbox/retry, and external push/email delivery
  remain Phase 6 work.

## Phase 6 — Preferences and delivery expansion — Task 6D DONE

- [x] Task 6A — Add per-type on/off and quiet hours; the emitter must enforce them for eligible
  notifications.
- [x] Task 6B — Add `notification_outbox`, retry, dedupe, and delivery status before external
  transports or multiple backend instances.
- [x] Task 6C — Add opt-in email and online push adapters behind the same internal emit contract.
  Brevo remains a transport implementation for email and must not be mixed with the OTP flow.
- [x] Task 6D — Add user channel preferences so explicit `IN_APP`, `EMAIL`, and online `PUSH`
  requests can be enabled or suppressed independently.

### Task 6A plan and contract

- Keep the existing profile settings endpoint as the source of truth for the global
  `notificationEnabled` flag and quiet-hours window. Quiet hours use `HH:mm`, the fixed MVP zone
  `Asia/Phnom_Penh`, and the half-open interval `[start, end)`; `start > end` means an overnight
  window such as `22:00-07:00`.
- Add `notification_preferences` for explicit per-user, per-type overrides. Types without a row
  are enabled by default. The authenticated user can list overrides with
  `GET /api/v1/profile/settings/notifications/preferences` and upsert one with
  `PUT /api/v1/profile/settings/notifications/preferences/{notificationType}`.
- Gate every `NotificationEmitter` request before the application event is published. Global off,
  an explicit type-off row, or an active quiet-hours window suppresses the request. A preference
  storage/read failure fails open and logs, so notification infrastructure cannot roll back RSVP,
  payment, moderation, or another source action.
- This task remains in-app only. It does not add an outbox, retry worker, push adapter, or email
  delivery; those remain later Phase 6 tasks.

### Task 6A implementation report — 2026-09-10

- Changed: extended `UserSetting` and the existing profile settings contract with global
  notification quiet-hours fields; added `NotificationPreference` persistence, repository, service,
  DTOs, and authenticated profile-settings endpoints for per-type overrides.
- Database: added and registered Liquibase migration `0059-add-notification-preferences-and-quiet-hours.sql`.
- Integration: `NotificationEmitterImpl` now consults a separate delivery policy before publishing
  an application event. Policy reads use a new transaction and fail open; global off, explicit
  type off, and active quiet hours suppress eligible in-app notifications.
- Tests/checks: added settings, preference-service, quiet-hours boundary, and emitter suppression
  coverage. The first development-host compile exposed three stale six-argument `failure(...)`
  calls in `AdminNotificationServiceImpl`; those were corrected. The first test run then exposed
  strict Mockito setup defects in the Admin, suspicious-login, and new preference tests; those
  fixtures were corrected. The user subsequently confirmed full `gradlew test` passed. Postman
  JSON parsing and `git diff --check` passed; local Codex Gradle execution remains blocked by its
  network/loopback restriction before compilation.
- Deferred: push/email adapters remain later work. Brevo remains separate from OTP delivery.

### Task 6B plan and contract — DONE

- Keep the existing after-commit business-action boundary. The shared notification event listener
  will enqueue the immutable request into `notification_outbox` in a separate transaction; the
  RSVP, payment, moderation, or other source transaction must never depend on outbox availability.
- The outbox stores the explicit recipient, template key, type, bounded JSON parameters, reference
  metadata, optional dedupe key, attempt count, next-attempt time, error text, and a delivery status.
  A recipient-scoped unique dedupe index is the final concurrency guard for repeated events.
- A scheduled worker claims due `PENDING` rows with a database lock, re-checks current delivery
  preferences, and delegates rendering/persistence to the existing `NotificationWriter`. Success
  becomes `DELIVERED`; current preferences can mark a row `SUPPRESSED`; failures use bounded
  exponential backoff and become `FAILED` after the retry limit.
- The worker is safe for multiple enabled instances at the row-claim boundary. The queue is
  at-least-once: writer dedupe and the database unique index prevent duplicate rows when a worker
  retries after a partial commit. The existing after-commit handoff still has a short process-crash
  gap before enqueue; an atomic same-transaction outbox is intentionally deferred until the source
  modules can opt into that stronger coupling.
- This task adds no public endpoint and therefore requires no Postman request. Push/email adapters
  remain deferred; Brevo stays an email transport concern separate from OTP.

### Task 6B implementation report — 2026-09-10

- Changed: added `NotificationOutbox` persistence, status transitions, JSON request-fact storage,
  recipient-scoped dedupe lookup, a row-locking poller, bounded retry/backoff, and terminal
  `FAILED`/`SUPPRESSED` states. The after-commit notification listener now enqueues durable facts;
  the existing writer still owns template lookup, language selection, rendering, and in-app row
  creation.
- Database/config: added and registered Liquibase migration `0060-create-notification-outbox.sql`
  with due-row and unique-dedupe indexes. Added `app.notification.outbox.*` settings for poll
  interval, initial delay, batch size, and maximum attempts. Scheduling can still be disabled with
  the existing `app.scheduling.enabled=false` switch.
- Reliability: due rows are selected with pessimistic locks so multiple enabled instances do not
  deliver the same row concurrently. Writer failures retry after 5 seconds, 30 seconds, 5 minutes,
  30 minutes, and 2 hours, then become `FAILED`. A preference change before delivery becomes
  `SUPPRESSED`; a policy infrastructure error fails open and keeps the durable request deliverable.
- Tests/checks: the user confirmed full `gradlew test` passed on the development host. This Codex
  workspace could not run Gradle: the wrapper first failed with `Permission denied: getsockopt`, and
  the escalated attempt stopped with `Unable to establish loopback connection` before compilation.
  `git diff --check` passed; no Postman JSON change was needed because the public API is unchanged.
- Release status: committed and pushed to `origin/dev`; no deployment was performed. The short
  process-crash gap between source commit and after-commit enqueue remains documented; an atomic
  same-transaction outbox is intentionally deferred until source modules opt into that coupling.
- Deferred: push/email adapters behind the same internal emit contract remain the next task. Brevo
  continues to be an email transport and remains separate from OTP delivery.

### Task 6C plan and implementation report — DONE (2026-09-10)

- Extend `NotificationRequest` with an explicit channel set. Existing domain calls remain backward
  compatible and default to `IN_APP`; no existing RSVP, payment, moderation, or scheduled trigger
  silently starts sending external email.
- Store one outbox row per selected channel, with a unique `(recipient, channel, dedupe_key)` guard.
  Each channel gets an independent delivery status/retry lifecycle, so an email failure cannot make
  an already-delivered in-app notification appear failed.
- Keep `IN_APP` behind the current `NotificationWriter`. Add a generic SMTP email transport that
  renders the same bilingual notification template, uses the verified `app.mail.from` sender, and
  has its own `app.notification.email.enabled` switch. It must not call the OTP `EmailService`.
- Add an authenticated WebSocket push transport to `/user/queue/notifications`, using the same
  localized title/message and navigation metadata. REST remains the source of truth when a client
  is offline; FCM/APNs, device tokens, and provider-specific credentials are intentionally deferred.
- The outbox worker dispatches by channel and keeps retry/backoff/dedupe semantics. Disabled or
  unavailable opt-in transports are not enqueued; this avoids a permanent retry storm when a
  provider is intentionally off.

Implementation report:

- Added `NotificationChannel`, `NotificationDelivery`, and `NotificationTransport` as the internal
  channel boundary. Existing emitters remain backward compatible and continue to default to
  `IN_APP`.
- Refactored the durable outbox worker to create independent rows and retry state per requested
  channel. Added `0061-add-notification-delivery-channels.sql` with the channel column and the
  `(recipient_user_id, channel, dedupe_key)` unique guard.
- Added the in-app adapter, generic Brevo/SMTP notification email adapter, and authenticated
  WebSocket push adapter. Email is controlled by `NOTIFICATION_EMAIL_ENABLED`; online push is
  controlled by `NOTIFICATION_PUSH_ENABLED` and uses `/user/queue/notifications`.
- Kept OTP delivery on the existing `MAIL_ENABLED` / `EmailService` path. No existing RSVP,
  payment, moderation, or scheduled trigger silently sends external email or push.
- Added transport, outbox fan-out, and WebSocket subscription tests. The first compile exposed
  duplicate test/application constructor signatures and a missing Mockito `when` import; those
  were fixed. The next test run exposed three worker fixtures without an outbox ID; the fixtures
  were corrected. The user then confirmed the full `gradlew test` build passed.
- Codex local Gradle execution remained unavailable because the wrapper/network environment could
  not establish its loopback connection; this did not block verification on the user's development
  host. `git diff --check` passed.
- Updated `MAIL_SERVICE.md` and this plan. No Postman collection change was needed because Task 6C
  adds no public REST endpoint. No deployment was performed.
- Deferred: offline FCM/APNs delivery, device-token registration, provider credentials, delivery
  receipts, and channel-specific user preference UI remain future work. REST/in-app history remains
  the source of truth when the client is offline.

### Task 6D plan and implementation report — DONE (2026-09-10)

- Add a separate per-user `notification_channel_preferences` table for the three channel values.
  Existing type preferences and the global notification setting remain the higher-level gates;
  missing channel rows are enabled by default for backward compatibility.
- Expose authenticated profile-settings endpoints to list channel preferences and update one channel.
  The request body remains the existing `{ "enabled": true|false }` shape.
- Apply the channel preference to explicit channel requests at the durable worker boundary. A
  multi-channel request is evaluated independently per outbox row, so disabling email or push does
  not suppress an in-app row. Existing four/seven-argument emitter calls remain `IN_APP` only.
- Keep channel preference failures fail-open like the existing notification policy, so a settings
  read problem cannot break RSVP, payment, moderation, or another source business action.
- Update the Postman collection and notification plan. FCM/APNs and device-token registration stay
  deferred; this task controls the current Brevo email and authenticated WebSocket transports only.

Implementation report:

- Added `NotificationChannelPreference` persistence, repository, service, response DTO, and
  authenticated profile-settings controller. `GET /api/v1/profile/settings/notifications/channels`
  returns all three channels with missing rows enabled by default; `PUT .../channels/{channel}`
  upserts one explicit override.
- Added and registered Liquibase migration `0062-create-notification-channel-preferences.sql`
  with a per-user/per-channel unique constraint and an enum-value check.
- Extended the delivery policy to apply channel overrides to single-channel outbox deliveries.
  Mixed requests remain independent, so disabling `EMAIL` or `PUSH` does not suppress `IN_APP`;
  preference read failures fail open like the existing type/global policy.
- Added service and policy tests for stable defaults, upsert/validation, disabled channels, and
  mixed-channel eligibility. The user confirmed the full `gradlew test` build passed; Postman JSON
  parsing and `git diff --check` passed as well.
- Updated the Postman collection with the new list/update requests and updated this plan. No
  deployment or push was performed.
- Deferred: FCM/APNs offline delivery, device-token registration, provider credentials, delivery
  receipts, and automatic channel selection by notification type remain future work.

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
