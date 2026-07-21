# Entertainment Domain — Implementation Plan

Status: Quiz + Reward pipeline + Badges done (2026-07-19); Challenges/Leaderboard/ShortContent to build. Scope: backend only.
Owner: (Entertainment / Gamification, per Team Assignment v5).

The gamification pillar: quizzes, daily challenges, badges, a leaderboard, and short
(reel) content. Plugs into the XP system (xp_history + users.total_xp) already used by
Community RSVP, Finance literacy, and Productivity focus sessions.

---

## Where Entertainment stands now

Measured 2026-07-19. Tables exist (migration 0010); the four services are stubs that
each return an empty list from a single `getAll()`.

| Feature | Tables | Service | Controller |
|---|---|---|---|
| Quiz | quizzes, quiz_questions, quiz_options, quiz_attempts, quiz_attempt_answers | **stub** | yes (list only) |
| Daily challenge | daily_challenges, user_challenges | **stub** | yes (list only) |
| Badge | badges, user_badges | **stub** | yes (list only) |
| Leaderboard | leaderboards | **stub** | yes (list only) |
| Short content | short_contents | none | none |

Key schema facts:
- **quiz_attempts** has NO unique(quiz,user) - replays are allowed; attempt answers are
  logged in quiz_attempt_answers. Options carry a `correct` boolean.
- **user_challenges** has `status` (NOT_STARTED/IN_PROGRESS/COMPLETED/CLAIMED),
  `progress`, `completed_at`, `claimed_at`, and UNIQUE(challenge,user). challenge_type is
  HABIT/TASK/FINANCE/QUIZ/EVENT/COMMUNITY/FOCUS.
- **user_badges** UNIQUE(user,badge); badge has a free-text `requirement` (not
  machine-evaluable) + icon_url.
- **leaderboards** stores rank+points per (user,category,period) - but ranks can also be
  computed on read from users.total_xp / xp_history.
- **short_contents** has like/save/share counters + creator_id + video/thumbnail URLs.

Reused infra: xp_history + users.total_xp (award pattern from EventServiceImpl);
LanguageResolver + LocalizedTextSelector for bilingual content.

---

## Phases (proposed)

### Phase 1 — Quiz — DONE
- [x] List quizzes (filter category / difficulty), detail = questions + options with the
  `correct` flag withheld; bilingual via LanguageResolver.
- [x] Submit (`POST /quizzes/{id}/attempts`): grades against quiz_options.correct, writes
  quiz_attempts + quiz_attempt_answers (a row per answered question), returns score +
  per-question correctness. XP = round(xpReward * correct/total), awarded once per quiz
  (existsBy...XpEarnedGreaterThan guard); replays log an attempt but pay 0.
- [x] History (`GET /quizzes/history`). Seed 0023 = one English quiz. Tests.

### Phase 2 — Daily Challenges
- List active challenges (deadline in future) with the user's status.
- Join (create user_challenge IN_PROGRESS), complete (mark COMPLETED), claim (award XP
  once, set CLAIMED). Progress is user/client-driven - no cross-domain auto-tracking
  (that would couple Entertainment to every other domain). Seed English challenges.
- Keeps the Home daily-challenge card real.

### Phase 3 — Badges — DONE
- [x] Migration 0024: added a `code` column to badges and seeded the 6 named milestone
  badges (Productivity Hero, Communication Helper, Financial Starter, Consistency Master,
  Quiz Champion, Event Explorer).
- [x] Catalog with an `earned` flag (`GET /badges`) + "my badges" (`GET /badges/mine`),
  bilingual.
- [x] Auto-unlock via BadgeAwarderImpl, wired into the reward pipeline (replaces the
  no-op). Rules keyed by badge `code`, evaluated from xp_history counts per source +
  total XP. Idempotent (existsBy + UNIQUE(user,badge)). Tests.

### Phase 4 — Leaderboard
- Ranking computed on read (see decision 3): OVERALL_XP from users.total_xp, plus
  weekly/monthly windows from xp_history. Top N + the current user's own rank.

### Phase 5 — Short content — DEFERRED
- Deferred like Chat (decision 4). short_contents stays entity+repo only. Short-form
  video needs a media pipeline and is a feature on its own; the app demos fine without it.

## Endpoints (under /api/v1/entertainment)

`/quizzes` (+ `/{id}`, `/{id}/attempts`), `/challenges` (+ `/{id}/join|complete|claim`,
`/mine`), `/badges` (+ `/mine`), `/leaderboard`, and (if included) `/short-content`.

## Conventions

Same as the rest: UUID PKs, BaseEntity, DTO in/out, ApiResponse envelope, PageResponse
for lists, owner-scoped lookups return not-found, soft delete via deletedAt, bilingual
via _en/_km + LanguageResolver, XP via xp_history + users.total_xp. English seed only;
Khmer left for the content team.

## Decisions (locked with product; revised 2026-07-19 after reviewing the user flow)

1. **Quiz XP = once per quiz, scaled by score.** `xp = round(xpReward * correct/total)`,
   granted only on the first completed attempt; replays still log an attempt but pay 0.
2. **Central reward pipeline (NEW).** The flow shows Earn XP -> Update User Level + XP
   History + Badge check. Add a shared `RewardService.award(user, source, sourceId, xp)`
   that bumps total_xp, recomputes users.level, writes xp_history, and auto-unlocks
   milestone badges. Retrofit the 4 existing inline XP sites (RSVP, quiz, focus, lesson)
   and add XP to habit check-in + task completion. Level formula: 100 XP per level
   (`level = 1 + total_xp/100`), documented and easy to tune.
3. **Badges = auto-unlock on milestones** (revised from ADMIN-grant). Seed the named
   badges from the flow (Productivity Hero, Communication Helper, Financial Starter,
   Consistency Master, Quiz champion, Event explorer) with XP/count thresholds; the reward
   pipeline unlocks them. Still expose list + earned flag + "my badges".
4. **Leaderboard = period x category, computed on read** (expanded). period =
   WEEKLY/MONTHLY/ALL_TIME; category = OVERALL_XP + PRODUCTIVITY/COMMUNITY_ENGAGEMENT/
   FINANCIAL_LEARNING/QUIZ_PERFORMANCE from xp_history.source_type. Top N + caller rank.
   **Friends ranking deferred** (needs the follow graph).
5. **Short content = build a basic version** (revised). List/detail by category +
   like/save/share counters + creator-profile link. No upload/media pipeline.
6. **Multiplayer quiz = deferred pending confirmation.** The flow has Find Opponent ->
   Start Match; real-time matchmaking is a feature on its own (WebSocket, like Chat).

## Revised phase order (dependency-driven)

0. **Reward pipeline** (foundation) - RewardService + level + retrofit + habit/task XP.
1. Quiz - DONE (single-player; will call RewardService after Phase 0).
3. **Badges** - catalog + earned flag + auto-unlock wired into RewardService.
2. **Daily Challenges** - join/complete/claim via RewardService.
4. **Leaderboard** - period x category on read.
5. **Short content** - list/detail + counters.
Deferred: Multiplayer quiz, Friends ranking, Chat.

## Follow-ups

- Khmer content for quizzes, challenges, badges needs review (English-only seed).
- Cross-domain challenge auto-progress (e.g. "complete 3 tasks") is deliberately out of
  scope - progress is user-driven for now.
