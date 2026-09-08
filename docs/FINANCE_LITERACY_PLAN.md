# Finance Domain — Phase 4: Financial Literacy — Plan

Status: DONE (2026-07-19). Part of backend/docs/FINANCE_PLAN.md (Phase 4).
Scope: backend only. Rewards are simulated (XP + demo wallet), never real money.
Last verified: 2026-09-06

Maps the "Financial Literacy Flow" figma: Open → Learning Dashboard → Choose Topic
→ Learning Content (Article / Khmer Tip / Short Video) → Finish Learning → Quiz →
Submit → Score → Earn EP Reward → Unlock Learning Budget.

---

## Decisions (locked with product)

1. **Unlock Learning Budget = simulated wallet bonus.** On first quiz pass, credit a
   fixed learning reward to the user's demo wallet as a new `REWARD` transaction.
2. **EP Reward = the existing XP system.** Add `lesson.xpReward` to `users.total_xp`
   and write an `xp_history` row (`sourceType = "FINANCE_LESSON"`), same pattern as
   Community RSVP. No separate points currency.
3. **Reward granted on quiz pass >= 60%, retakes allowed.** XP + wallet credit are
   granted once, on the first pass. Retakes are allowed; the best score is kept.
4. **Include:** `media_url` column (video lessons), a quiz-attempt history table, and
   English sample seed data.

## Where Financial Literacy stands before Phase 4

Tables `financial_lessons` and `financial_quiz_questions` exist (migration 0008) with
bilingual columns; both repos are bare `JpaRepository`. **No service, controller, or
DTOs.** Two schema gaps: no `lesson_progress` (can't mark completion or award once)
and no quiz-attempt table.

Reused infra: `xp_history` + `users.total_xp` (via XpHistoryRepository, cross-module
from entertainment, as EventServiceImpl already does); the demo wallet (Phase 5) for
the learning reward; LanguageResolver + LocalizedTextSelector for bilingual content.

---

## Schema — migration 0020

- **`lesson_progress`** — `progress_id` PK, `user_id` FK, `lesson_id` FK,
  `completed_at`, `best_score` INT, `quiz_passed` BOOLEAN NOT NULL DEFAULT false,
  `rewarded` BOOLEAN NOT NULL DEFAULT false, audit cols, **UNIQUE(user_id, lesson_id)**.
  `rewarded` gates the whole reward bundle (XP + wallet) so a retake never double-pays.
- **`finance_quiz_attempts`** — `attempt_id` PK, `user_id` FK, `lesson_id` FK,
  `score` INT NOT NULL, `passed` BOOLEAN NOT NULL, audit cols. One row per submission
  (history). Indexed by (user_id, lesson_id).
- **`financial_lessons` ADD `media_url` VARCHAR(500)** — for the Short Video type.
- `TransactionType.REWARD` added to the enum (string column, no DDL needed).
- Written with `--rollback`, following 0018/0019.

## Canonical enums

- `LiteracyTopic`: SAVING_MONEY, BUDGETING, DEBT_AWARENESS, INVESTING_BASICS,
  EMERGENCY_FUND, SMART_SPENDING. Lets the dashboard list all six topics even before
  a lesson exists. Stored in the existing `topic` string column.
- `LessonContentType`: ARTICLE, KHMER_TIP, VIDEO. Stored in `content_type`.

## Endpoints — /api/v1/finance/literacy

| Flow node | Endpoint |
|---|---|
| Learning Dashboard | `GET /dashboard` — 6 topics, each with lesson count + completed count; total literacy XP |
| Choose Topic / list | `GET /lessons?topic=` — summaries with a `completed` flag |
| Learning Content | `GET /lessons/{id}` — bilingual content, contentType, mediaUrl |
| Finish Learning | `POST /lessons/{id}/complete` — sets `completed_at` (no reward) |
| Quiz Learning | `GET /lessons/{id}/quiz` — questions + options, **correct answer stripped** |
| Submit / Score / Reward | `POST /lessons/{id}/quiz/submit` — grades, records attempt, awards once |

Bilingual output resolved via Accept-Language + LanguageResolver/LocalizedTextSelector.

## Submit-quiz logic

1. Grade answers (`answers`: questionId → chosen option letter) against `correctAnswer`.
   `score = correct * 100 / total`; `passed = score >= 60`.
2. Always insert a `finance_quiz_attempts` row (history).
3. Upsert `lesson_progress`: `best_score = max(old, new)`, `quiz_passed |= passed`,
   set `completed_at` if unset.
4. If `passed && !rewarded`: add `xpReward` to `total_xp` + `xp_history` row; credit the
   demo wallet with the learning reward (`REWARD` txn, "SIMULATED" log); set
   `rewarded = true`.
5. Return score, correct/total, per-question correctness + explanation, `passed`,
   `xpEarned` (0 on a repeat pass), `learningBudgetCredited`.

Reward constants: `PASS_THRESHOLD = 60`, `LEARNING_REWARD` = a small fixed USD amount.

## DTOs

`LiteracyDashboardResponse`, `LessonSummaryResponse`, `LessonDetailResponse`,
`QuizQuestionResponse` (no correct answer), `QuizSubmitRequest`, `QuizResultResponse`.

## Tests

Grading math; once-only reward across retakes; pass threshold boundary; correct answer
hidden on GET quiz; bilingual selection; progress upsert + best-score; ownership. Verify
via javac + the RunOne launcher (sources-free classpath — see build-gotchas).

## Seed (English)

A few English lessons + quiz questions per topic so the endpoints are demoable. Khmer
lesson/quiz content is a content-team task — not machine-translated here.

## Conventions

Same as the rest of Finance: UUID PKs, BaseEntity, DTO in/out, ApiResponse envelope,
owner-scoped lookups return not-found, soft delete via deletedAt, BigDecimal money,
bilingual via _en/_km + LanguageResolver.

## Follow-ups / notes

- Khmer content for lessons, quizzes, and the finance tip (Phase 6) still needs review.
- Home dashboard finance card now uses live expense spend; bilingual finance content review
  remains.
