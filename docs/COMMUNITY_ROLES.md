# GenLink KH — Community Roles, Ownership & Moderation

> Role management for communities: promote/demote, ownership transfer, and role-gated
> post moderation. Backend only. Landed 2026-07-28.

---

## 1. Roles

`CommunityRole` = `OWNER`, `ADMIN`, `MODERATOR`, `MEMBER`. Every membership row
(`community_members`) carries one. The values existed from the start, but until now nothing
could assign anything but `OWNER` (to the creator) and `MEMBER` (to everyone who joins) —
this feature makes the middle two reachable.

| Role | Community admin (edit, roles, ownership) | Join requests | Moderate posts | Post/participate |
|---|---|---|---|---|
| OWNER | Full, incl. delete + transfer | Yes | Yes | Yes |
| ADMIN | Edit settings; manage MOD/MEMBER roles | Yes | Yes | Yes |
| MODERATOR | — | — | Yes | Yes |
| MEMBER | — | — | — | Yes |

`CAN_MANAGE = {OWNER, ADMIN}` is the gate for editing the community and handling join
requests (unchanged — ADMIN already had this). `MODERATOR_ROLES = {OWNER, ADMIN, MODERATOR}`
is the gate for removing posts.

---

## 2. Promote / demote — `PUT /api/v1/community/communities/{id}/members/{userId}/role`

Body: `{ "role": "ADMIN" | "MODERATOR" | "MEMBER" }`. Returns the updated
`CommunityMemberResponse`.

Rules (enforced in `CommunityServiceImpl.changeMemberRole`):

- Caller must be **OWNER or ADMIN** — anyone else gets **404** (not 403), so the endpoint
  never confirms the community to a non-manager.
- **OWNER is not a valid target role** — use transfer-ownership. Sending `OWNER` → 400
  `COMMUNITY_ROLE_INVALID`.
- **You cannot change your own role** → 400 `COMMUNITY_ROLE_SELF`.
- **The owner's role is immutable here** → 400 `COMMUNITY_ROLE_OWNER_IMMUTABLE`.
- **An ADMIN may only manage MODERATOR and MEMBER.** It cannot mint a peer admin or demote
  another admin → 400 `COMMUNITY_ROLE_ADMIN_FORBIDDEN`. Administering admins is owner-only.
- Setting the role a member already has is an idempotent no-op.

Why not-found vs forbidden: it matches the module-wide convention (ownership-scoped lookups
return not-found) so the API does not leak the existence of communities you can't manage.

---

## 3. Ownership transfer — `POST /api/v1/community/communities/{id}/transfer-ownership/{userId}`

Returns the `CommunityResponse` from the (now ADMIN) caller's perspective.

- **Current owner only** — everyone else gets **404**.
- Target must be an **active member**; a non-member → 404 `Member not found`.
- Transferring to yourself → 400 `COMMUNITY_ALREADY_OWNER`.
- In one transaction: the **outgoing owner becomes ADMIN**, the target becomes OWNER. So the
  community always has **exactly one owner**. The outgoing owner is deliberately kept as ADMIN
  rather than dropped to MEMBER — demoting them would lock them out of what they built.
- `communities.created_by_user_id` is **not** changed — it is the historical creator record,
  not the current owner. Ownership lives in the member role.

This also resolves the old dead-end where `leave()` threw `COMMUNITY_OWNER_CANNOT_LEAVE`: an
owner now transfers first, then leaves.

---

## 4. Post moderation — `DELETE /api/v1/community/posts/{id}/moderate`

Lets a community moderator remove a **member's** post, as opposed to `DELETE
/api/v1/community/posts/{id}` which is author-only.

- Allowed to **OWNER / ADMIN / MODERATOR** of the post's community.
- Soft delete (`deleted_at`), and `postCount` is decremented (create incremented it).
- After removal, the post author receives the bilingual `COMMUNITY_POST_MODERATED` in-app
  notification with `POST + postId` navigation metadata and a recipient-scoped dedupe key.
- A post that belongs to **no community** has no moderators → 404.
- A non-moderator (plain member or non-member) → 404, not 403.

---

## 5. Post reporting & moderation queue

Built 2026-07-28 (migration 0027). Members flag posts; a community's moderators review and
act. Full detail in `POST_REPORTING.md`. In brief:

- `POST /community/posts/{id}/report` — any authenticated user flags a post (idempotent, one
  report per user).
- `GET /community/posts/community/{communityId}/reported` — a moderator's queue for that
  community, most-reported first. OWNER/ADMIN/MODERATOR only.
- Act on a reported post: `DELETE /community/posts/{id}/moderate` (remove it) or
  `POST /community/posts/{id}/dismiss-reports` (keep it, clear the flags).

## 6. Not built yet

- **Forum discussion moderation stays global.** `forum_discussions` is category-scoped, not
  community-scoped (no `community_id`), so its existing `discussion_reports` are an
  app-admin concern, not a community-role one. Community MODERATOR does not cover the forum.
- **Removed-member management** (kick/ban a member from a community) is not part of this
  change — only their posts can be moderated, not their membership.
