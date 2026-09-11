# GenLink KH — Forum Discussion Moderation

> Admin review of reported forum discussions. Backend only. Landed 2026-07-28.
> Completes the moderation story alongside `POST_REPORTING.md` (community posts).

---

## 1. Why this is admin-gated, not moderator-gated

Community posts are moderated by **community roles** (OWNER/ADMIN/MODERATOR) because a post
belongs to a community that has those roles. **Forum discussions do not.** `forum_discussions`
is **category-scoped** (a `category` text field), with no `community_id` — there is no "owner"
or "moderator" of a category. So forum moderation is an **app-admin** concern, gated by
`ROLE_ADMIN` via `@PreAuthorize("hasRole('ADMIN')")` (method security is already enabled;
same pattern as the Opportunity admin endpoints).

Reporting itself already existed (`discussion_reports`, migration 0019) but nothing reviewed
the queue — a report did nothing beyond a log line. This adds the review + act flow. **No
migration** was needed: the table and `report_count` were already there.

---

## 2. Endpoints (all ROLE_ADMIN)

| Method | Path | Purpose |
|---|---|---|
| GET | `/api/v1/community/discussions/reported` | Queue: reported discussions, most-reported first (paginated). |
| DELETE | `/api/v1/community/discussions/{id}/moderate` | Remove any discussion (soft delete). |
| POST | `/api/v1/community/discussions/{id}/dismiss-reports` | Keep it, clear the flags. |

A non-admin caller is rejected by Spring Security before the controller runs (403). The
`/reported` literal path is declared before `/{discussionId}` so it is never shadowed.

---

## 3. Author is revealed in the queue — on purpose

Ordinary responses (`DiscussionResponse`) **hide** the author of an anonymous discussion; the
DTO boundary is the only thing enforcing anonymity. The moderation queue is the deliberate
exception: `ReportedDiscussionResponse` **reveals the author even for anonymous discussions**,
with a `postedAnonymously` flag so the admin knows it was anonymous to the public.

This is why the author is stored on every row in the first place — anonymity is a display
rule for other users, not a shield from moderation. `ReportedDiscussionResponse` is only ever
returned from the ADMIN-gated queue, never to ordinary users, so anonymity is preserved
everywhere else.

---

## 4. Act — remove vs dismiss

- **Remove** (`/moderate`): soft-deletes the discussion (`deleted_at`). It drops out of the
  queue and out of `browse` (both filter `deleted_at IS NULL`). Works regardless of author —
  an admin can remove anyone's discussion, unlike the author-only `DELETE /discussions/{id}`.
  After the save, the stored discussion author receives the bilingual
  `FORUM_DISCUSSION_MODERATED` notification with the discussion title, `DISCUSSION + discussionId`
  reference, and a recipient-scoped dedupe key. This also applies when the discussion was posted
  anonymously; anonymity is still preserved in ordinary public responses.
- **Dismiss** (`/dismiss-reports`): reviewed and fine. Deletes the `discussion_reports` rows
  and zeroes `report_count`, so it leaves the queue but the discussion stays. A later report
  from a **new** user re-opens it (old rows are gone, so the unique constraint no longer
  blocks them).

The queue lists discussions with `report_count > 0` and `deleted_at IS NULL`, ordered by
`report_count` desc, author `JOIN FETCH`ed (one query per page).

---

## 5. Scope notes

- **Manual review only** — no report-count threshold and no auto-hide. A reported discussion
  stays visible until an admin acts. Auto-hide-at-N-reports could be layered on later.
- **Needs a seeded admin.** `Role` defaults to `USER`, so nothing is `ROLE_ADMIN` out of the
  box. Flip a user to ADMIN in the database (or seed one) to exercise these endpoints — the
  same caveat as the Opportunity admin endpoints.
- **Reasons are not aggregated** in the queue (only the count), matching the post queue. A
  per-discussion report-detail view could be added if reviewers need the reasons.
- This closes the last moderation gap: community **posts** (by community role) and forum
  **discussions** (by app admin) are both now reviewable.
