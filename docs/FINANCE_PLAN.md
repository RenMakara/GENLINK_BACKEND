# Finance Domain — Implementation Plan

Status: Phases 1-3, 5, 6 done; Phase 4 not started. Owner: Sela (Finance, per Team Assignment v5).
Order note: Phase 5 done before 4 (per the suggested order) to unblock Community paid RSVP;
Phase 6 (BFF) done before 4 since it only needs the finished phases.
Scope: backend only. Payments are simulation only — no real banking, no real KHQR.

---

## Where Finance stood before Phase 1

Measured 2026-07-18. Tables exist (migration 0008); services are stubs.

| Feature | Entity | Repo | Service | Controller |
|---|---|---|---|---|
| Expense | yes | yes | **done** | yes |
| Budget | yes | yes | **done** | yes |
| Saving goal | yes | yes | **done** | yes |
| Financial lesson | yes | yes | none | none |
| Demo wallet | yes | yes | **done** | **done** |

`ExpenseServiceImpl.create` echoes the request back with a null id and saves
nothing — the same lying stub as Community. Treat "endpoint exists" as unproven.

Two schema gaps (same pattern as Community):
- **No `lesson_progress` table.** The data dictionary had it; migration 0008 did
  not create it. Needed to award lesson XP once per user (Phase 4).
- **No finance quiz-attempt table.** `financial_quiz_questions` exists, but there
  is nowhere to record a user's answer/score to award quiz XP once (Phase 4).

Demo wallet already has a real consumer: Community paid RSVP debits it. Before
Phase 5 there was no way to CREATE a wallet or add balance, so paid RSVP failed
with DEMO_WALLET_NOT_FOUND. Phase 5 (done) adds get-or-create + top-up, so that
path now works.

---

## Phase 1 — Expense Tracker (foundation) — DONE

- [x] `ExpenseServiceImpl` for real: create, get, update, soft delete,
      owner-scoped (another user's id reads as not found).
- [x] `GET /api/v1/finance/expenses` — paginated, filter by category and by
      month/year (date range, not EXTRACT, to stay portable and avoid the
      `:date IS NULL` Postgres trap).
- [x] `GET /api/v1/finance/expenses/summary?month=&year=` — total plus a
      per-category breakdown, for the spending chart.
- [x] Tests.

No dependencies. Everything else reads from expenses.

## Phase 2 — Budget Planner — DONE

- [x] Monthly budget CRUD, UNIQUE(user, month, year) enforced with a clear
      BUDGET_ALREADY_EXISTS error, not a raw constraint 500.
- [x] Category budgets (budget_categories); update replaces them wholesale.
- [x] **Spent/remaining/status computed from expenses on read**, not a cached
      counter. The columns exist, but a cached counter that must track every
      expense create/edit/delete is exactly the drift bug hit repeatedly in
      Community. Compute `spent = SUM(expenses in that month)` on read instead
      (ExpenseRepository.sumForMonth for total, sumByCategory per category).
- [x] Budget alert = status SAFE / WARNING / LIMIT_REACHED from spent vs total
      (WARNING at >= 80%, LIMIT_REACHED at >= 100%).
- [x] `GET /budgets` list (light, no categories); `GET /budgets/current`,
      `GET /budgets/{id}` (with category breakdown). Tests.

## Phase 3 — Saving Goals — DONE

- [x] Goal CRUD, target amount/date, owner-scoped (another user's id reads as
      not found). Update re-derives completion from the new target; a CANCELLED
      goal stays cancelled.
- [x] Deposit (`POST /savings/{id}/deposits`): appends a saving_deposits row and
      adds to currentAmount in the same transaction, flips status to COMPLETED
      when the target is reached. Deposits into a cancelled goal are rejected.
      Because deposits are append-only (no edit/delete), the stored counter cannot
      drift, so it stays the source of truth rather than a recompute.
- [x] Deposit history (`GET /savings/{id}/deposits`), most recent first.
- [x] Progress bar data: response carries remainingAmount (floored at 0) and
      progressPercent (0-100, capped) alongside current/target. Tests.

## Phase 4 — Financial Literacy

- [ ] Lessons: list by topic, detail. Bilingual (title_en/km, content_en/km) via
      LanguageResolver + LocalizedTextSelector.
- [ ] **Migration: lesson_progress** (user, lesson, completed, unique(user,lesson))
      so lesson XP is awarded once.
- [ ] Quiz: questions per lesson, submit answers -> score. XP once per lesson.
      **Migration: a finance quiz attempt/answer table** if per-attempt history is
      wanted; otherwise fold "passed" into lesson_progress.
- [ ] XP writes to xp_history + users.total_xp, consistent with Community RSVP.

## Phase 5 — Demo Wallet / KHQR Simulation — DONE

- [x] Get-or-create wallet (`GET /finance/wallet`), balance + currency (USD).
      Created on first access; the UNIQUE(user_id) race is caught and re-read.
- [x] Top-up (`POST /finance/wallet/top-up`, DEPOSIT) and mock KHQR payment
      (`POST /finance/wallet/pay`, MOCK_KHQR_PAYMENT); paginated transaction
      history (`GET /finance/wallet/transactions`). Every balance move writes a
      DemoTransaction and logs "SIMULATED" - never implies a real transfer.
      Insufficient balance throws before any write (no FAILED row), matching the
      paid-RSVP debit path.
- [x] Unblocks Community paid RSVP: a user can now open a wallet and top up, so
      settlePayment no longer dead-ends at DEMO_WALLET_NOT_FOUND. Tests.

## Phase 6 — BFF — DONE

- [x] `GET /api/v1/mobile/finance-dashboard` (was a "TODO" stub returning a
      message): month overview (spent + budget total/remaining/status), top
      spending categories, saving progress, recent expenses, demo wallet balance,
      a rotating tip, and quick-action keys. Aggregation lives in
      MobileFinanceBffServiceImpl (bff module), resolving the user by email like
      the home BFF. Every section degrades independently - a brand-new user gets a
      well-formed body with nulls/empties. Spend is computed live from expenses
      (not Budget.spentAmount). Tests.
- [ ] Follow-ups: the tip is English-only - needs a bilingual finance-tip seed or
      reviewed Khmer. The home dashboard's finance card still reads the stale
      Budget.spentAmount counter (shows spent=0) - flagged separately to fix the
      same live-spend way.

---

## Conventions (same as Community)

UUID PKs, BaseEntity, DTO in/out (never expose entities), ApiResponse envelope,
PageResponse for lists, owner-scoped lookups return not-found (not forbidden),
soft delete via deletedAt, LAZY + LEFT JOIN FETCH where a list needs a relation,
BigDecimal for money, bilingual system content via _en/_km + LanguageResolver.

## Suggested order

Phase 1 + 2 first (expense + budget is the core loop), then 3, then 5 (unblocks
paid RSVP), then 4, then 6. Reassess after 2.
