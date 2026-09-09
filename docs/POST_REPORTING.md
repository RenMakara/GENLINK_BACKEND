# GenLink KH — Post Reporting & Moderation Queue

> Members flag posts; a community's moderators review and act. Backend only.
> Landed 2026-07-28 (migration 0027). Builds on the community roles in `COMMUNITY_ROLES.md`.

---

## 1. Overview

Posts previously had no reporting at all — a community moderator had nothing to review. This
adds a report signal on posts, a per-community queue of reported posts, and two moderator
actions (remove or dismiss). It mirrors the existing forum `discussion_reports` (migration
0019); the difference is that posts can belong to a community, so the queue and the actions
are gated by community role.

---

## 2. Schema (migration 0027)

- `posts.report_count INTEGER NOT NULL DEFAULT 0` — cached count of distinct reporters.
- `post_reports` — one row per (post, reporter): `reason` (VARCHAR 80), `details` (TEXT),
  plus audit columns, with `UNIQUE(post_id, user_id)`. That constraint is what makes
  `report_count` mean "distinct people flagged this" rather than "how many taps".
- Indexes on `post_reports(post_id)` and `posts(report_count)`.

No enum for `reason` — free text, same as discussion reports; the moderation vocabulary is
not settled and baking one in now would be guessing.

---

## 3. Endpoints

| Method | Path | Who | Purpose |
|---|---|---|---|
| POST | `/community/posts/{id}/report` | any authenticated user | Flag a post. |
| GET | `/community/posts/community/{communityId}/reported` | OWNER/ADMIN/MODERATOR | Queue, most-reported first (paginated). |
| POST | `/community/posts/{id}/dismiss-reports` | OWNER/ADMIN/MODERATOR | Keep the post, clear its flags. |
| DELETE | `/community/posts/{id}/moderate` | OWNER/ADMIN/MODERATOR | Remove the post (from COMMUNITY_ROLES). |

`report` body: `{ "reason": "SPAM", "details": "optional context" }`.

Every moderator refusal is **404, not 403** — the module-wide no-leak convention.

---

## 4. Behaviour

### Report

- Loads a live post (`deleted_at IS NULL`) or 404.
- Idempotent per user: if this user already reported the post, it is a no-op — no second
  row, no double count (also guaranteed by the unique constraint).
- Otherwise inserts a `post_reports` row and `report_count += 1`. The reporter is recorded
  so a moderator can see who flagged it.
- Any authenticated user may report any visible post; there is no community-membership
  requirement to report (only to moderate).

### Queue

- `requireCommunityModerator(communityId)` — caller must be an active OWNER/ADMIN/MODERATOR
  of that community, else 404.
- Returns live posts in the community with `report_count > 0`, ordered by `report_count`
  desc, as `ReportedPostResponse` (post essentials + `reportCount`). The author is
  `JOIN FETCH`ed, so the page costs one query, not one per row. Deliberately not a full
  `PostResponse` — the queue does not need likedByMe / savedByMe / hashtags.

### Act — remove vs dismiss

- **Remove** (`/moderate`, from COMMUNITY_ROLES): soft-deletes the post and decrements
  `postCount`. It drops out of the queue because the queue filters `deleted_at IS NULL`, then
  emits `COMMUNITY_POST_MODERATED` to the post author with the community name, `POST + postId`
  reference, and a recipient-scoped dedupe key.
- **Dismiss** (`/dismiss-reports`): the report was reviewed and the post is fine. Deletes the
  `post_reports` rows and zeroes `report_count`, so it leaves the queue but the post stays.
  A later report from a **new** user re-opens it (the old rows are gone, so the unique
  constraint no longer blocks them).

---

## 5. Scope notes

- **`report` returns void** (like the forum's `report`); the client does not need the running
  count to render a one-shot "Reported" state.
- **Global / non-community posts** can still be reported, but there is no queue for them —
  moderation is community-scoped. An app-admin queue for public-post reports is not built.
- **Reasons are not aggregated in the queue.** The queue shows the count, not a breakdown of
  reasons; a per-post report-detail view could be added if moderators need it.
- **Self-reporting is allowed** (not blocked), matching the forum's behaviour; it is harmless
  and the reporter is recorded.
