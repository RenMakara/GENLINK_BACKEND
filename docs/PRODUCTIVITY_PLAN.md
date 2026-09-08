# Productivity Domain — Implementation Plan

Status: all 6 phases done (2026-07-19). Scope: backend only.
Owner: (Productivity, per Team Assignment v5).
Last verified: 2026-09-06

The "Study & Productivity" super-app pillar: habits, tasks, goals, a focus timer, and
motivation content. Powers the Home dashboard's today-progress and daily-motivation
cards (their read side already exists in the home BFF).

---

## Where Productivity stands now

Measured 2026-07-19. Tables exist (migration 0007); the three services are lying stubs.

| Feature | Entity | Repo | Service | Controller |
|---|---|---|---|---|
| Habit | yes | yes | **stub** | yes |
| Task | yes | yes | **stub** | yes |
| Goal + milestones | yes | yes | **stub** | yes |
| Focus session | yes | yes | none | none |
| Motivation content | yes | yes | none | none |
| Saved motivation | yes | yes | none | none |

`HabitServiceImpl.create` returns the request back with a null id and saves nothing —
the same lying stub as Community/Finance were. Treat "endpoint exists" as unproven.

Reused infra: XP system (`xp_history` + `users.total_xp`) for focus-session rewards;
LanguageResolver + LocalizedTextSelector for bilingual motivation content; the home
BFF already reads habit/task counts + motivation.

Key entity facts:
- **habit_check_ins** has `UNIQUE(habit_id, user_id, check_in_date)` → daily check-in is
  naturally idempotent, and streak/rate can be derived from it.
- **goal_milestones** (title, completed) → goal progress is milestones done / total.
- **focus_sessions** carries `xp_earned`, `duration_minutes`, `started_at/ended_at`.
- **user_saved_motivations** has `UNIQUE(user_id, motivation_id)` → idempotent save.
- **motivation_contents** carries a `saved_count` counter.

---

## Phases (proposed)

### Phase 1 — Habits — DONE
- [x] Habit CRUD, owner-scoped (another user's id reads as not found), soft delete.
- [x] **Daily check-in** (`POST /habits/{id}/check-in`): idempotent via the unique
  (habit,user,date) - updates the existing row; `completed=false` undoes it.
- [x] **streak + completionRate computed on read from check-ins**, not the stored
  counters. Streak = consecutive completed days ending today (or yesterday when today
  is not done yet, so a streak is not lost before the day's check-in); rate = completed
  days over the last 30, as a percent. `checkedInToday` flag included. Tests.

### Phase 2 — Tasks — DONE
- [x] Task CRUD, owner-scoped, soft delete; `POST /tasks/{id}/complete?completed=`
  to complete/reopen.
- [x] List with optional filters: category, priority, completed, and scope
  (today / overdue / upcoming). Filters applied in memory (bounded per-user list) to
  avoid the `:param IS NULL` Postgres trap. `overdue` computed on read (past due +
  not completed). Tests.

### Phase 3 — Goals & milestones — DONE
- [x] Goal CRUD, owner-scoped, soft delete; milestone add / complete-reopen / delete
  (milestones nested under the goal, verified via findByIdAndGoalId).
- [x] **progressPercentage + status computed from milestones** (done/total): NOT_STARTED
  / IN_PROGRESS / COMPLETED, auto-flipping to COMPLETED at 100%. Stored columns are also
  synced on milestone changes but the response computes from milestones. Tests.

### Phase 4 — Focus sessions (Pomodoro) — DONE
- [x] Start a session (`POST /focus-sessions/start`), complete it
  (`POST /focus-sessions/{id}/complete`). Completion is idempotent - XP is paid once.
- [x] On completion award duration-scaled XP `min(30, minutes/5)` to xp_history
  (sourceType FOCUS_SESSION) + users.total_xp; a sub-5-minute session earns 0.
- [x] Paginated history (`GET /focus-sessions`) and stats (`GET /focus-sessions/stats`:
  today's + all-time sessions/minutes/xp). Tests.

### Phase 5 — Motivation — DONE
- [x] List motivation content (bilingual, optional ?category), detail; seed 0022 adds
  English samples (Khmer left for the content team).
- [x] Save / unsave (idempotent), each carrying the user's `saved` flag; saved_count is
  bumped atomically (@Modifying, clear/flushAutomatically) only on a real save/unsave,
  and the UNIQUE(user,motivation) race is caught. "My saved" list. Tests.

### Phase 6 — Productivity dashboard BFF — DONE
- [x] `GET /api/v1/mobile/productivity-dashboard` (was a "TODO" stub): today's habits +
  per-habit check-in state, tasks summary (total/completed/dueToday/overdue), top goals
  with progress, focus stats (today + total XP), a rotating motivation card. In
  MobileProductivityBffServiceImpl (bff module), by-email like the home BFF; every
  section degrades independently. Goal progress read from the stored synced columns
  (no per-goal milestone query). Tests.

## Endpoints (under /api/v1/productivity, existing controllers extended)

`/habits` (+ `/{id}/check-in`), `/tasks`, `/goals` (+ `/{id}/milestones`),
`/focus-sessions`, `/motivation` (+ `/{id}/save`, `/saved`).

## Conventions

Same as Finance/Community: UUID PKs, BaseEntity, DTO in/out, ApiResponse envelope,
PageResponse for lists, owner-scoped lookups return not-found, soft delete via
deletedAt, compute-on-read over cached counters where practical, bilingual via
_en/_km + LanguageResolver.

## Decisions (locked with product)

1. **Habit streak/rate = computed on read** from habit_check_ins (drift-proof). The
   stored streak_count/completion_rate columns are written but not trusted.
2. **Focus-session XP = scaled by duration**: `min(CAP, durationMinutes / 5)` with a
   cap (30 XP), written to xp_history + users.total_xp.
3. **Include the Productivity dashboard BFF (Phase 6)** — the former
   MobileProductivityDashboardResponse stub is now implemented.
4. **Motivation seed = English samples**; Khmer left for the content team.

## Follow-ups

- Khmer motivation content needs review (English-only seed).
- Reminders (habit/task reminder_time) are stored but firing them is the Notification
  domain's job — out of scope here.
