# Finance Domain — Implementation Plan

Status: Phase 1 done (2026-07-18); Phases 2-6 not started. Owner: Sela (Finance, per Team Assignment v5).
Scope: backend only. Payments are simulation only — no real banking, no real KHQR.

---

## Where Finance stood before Phase 1

Measured 2026-07-18. Tables exist (migration 0008); services are stubs.

| Feature | Entity | Repo | Service | Controller |
|---|---|---|---|---|
| Expense | yes | yes | **stub** | yes |
| Budget | yes | yes | **stub** | yes |
| Saving goal | yes | yes | **stub** | yes |
| Financial lesson | yes | yes | none | none |
| Demo wallet | yes | yes | none | none |

`ExpenseServiceImpl.create` echoes the request back with a null id and saves
nothing — the same lying stub as Community. Treat "endpoint exists" as unproven.

Two schema gaps (same pattern as Community):
- **No `lesson_progress` table.** The data dictionary had it; migration 0008 did
  not create it. Needed to award lesson XP once per user (Phase 4).
- **No finance quiz-attempt table.** `financial_quiz_questions` exists, but there
  is nowhere to record a user's answer/score to award quiz XP once (Phase 4).

Demo wallet already has a real consumer: Community paid RSVP debits it. But there
is no way to CREATE a wallet or add balance yet, so paid RSVP fails with
DEMO_WALLET_NOT_FOUND until Phase 5 lands.

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

## Phase 2 — Budget Planner

- [ ] Monthly budget CRUD, UNIQUE(user, month, year).
- [ ] Category budgets (budget_categories).
- [ ] **Spent/remaining/status computed from expenses on read**, not a cached
      counter. The columns exist, but a cached counter that must track every
      expense create/edit/delete is exactly the drift bug hit repeatedly in
      Community. Compute `spent = SUM(expenses in that month)` on read instead.
- [ ] Budget alert = status SAFE / WARNING / LIMIT_REACHED from spent vs total.

## Phase 3 — Saving Goals

- [ ] Goal CRUD, target amount/date.
- [ ] Deposit: adds to currentAmount, writes a saving_deposits row, flips status
      to COMPLETED when the target is reached. Deposit history. Progress bar data.

## Phase 4 — Financial Literacy

- [ ] Lessons: list by topic, detail. Bilingual (title_en/km, content_en/km) via
      LanguageResolver + LocalizedTextSelector.
- [ ] **Migration: lesson_progress** (user, lesson, completed, unique(user,lesson))
      so lesson XP is awarded once.
- [ ] Quiz: questions per lesson, submit answers -> score. XP once per lesson.
      **Migration: a finance quiz attempt/answer table** if per-attempt history is
      wanted; otherwise fold "passed" into lesson_progress.
- [ ] XP writes to xp_history + users.total_xp, consistent with Community RSVP.

## Phase 5 — Demo Wallet / KHQR Simulation

- [ ] Get-or-create wallet, balance.
- [ ] Top-up (simulated deposit), scan mock KHQR, mock payment, transaction
      history. All simulation - label it, never imply a real transfer.
- [ ] Unblocks Community paid RSVP (currently DEMO_WALLET_NOT_FOUND with no wallet).

## Phase 6 — BFF

- [ ] `GET /api/v1/mobile/finance-dashboard` (currently a "TODO" stub): monthly
      summary, budget remaining, saving progress, recent expenses, a financial
      tip, quick actions.

---

## Conventions (same as Community)

UUID PKs, BaseEntity, DTO in/out (never expose entities), ApiResponse envelope,
PageResponse for lists, owner-scoped lookups return not-found (not forbidden),
soft delete via deletedAt, LAZY + LEFT JOIN FETCH where a list needs a relation,
BigDecimal for money, bilingual system content via _en/_km + LanguageResolver.

## Suggested order

Phase 1 + 2 first (expense + budget is the core loop), then 3, then 5 (unblocks
paid RSVP), then 4, then 6. Reassess after 2.
