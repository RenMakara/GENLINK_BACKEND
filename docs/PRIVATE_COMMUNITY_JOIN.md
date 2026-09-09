# GenLink KH — Private Community Join & Approval

> How joining a community works, and how PRIVATE communities gate membership behind
> owner/admin approval. Backend only. Landed 2026-07-28.

---

## 1. Overview

A community is `PUBLIC` or `PRIVATE` (`CommunityPrivacy`). Joining behaves differently:

- **PUBLIC** — joining is immediate. The caller becomes an `ACTIVE` member at once.
- **PRIVATE** — joining creates a **pending request**. An owner or admin must approve it
  before the caller becomes a member. Until then they are not a member in any sense.

This mirrors the follow flow for `PRIVATE` profiles, which already produced `PENDING`
follow requests, so the pattern is consistent across the community domain.

---

## 2. The bug this closed

Before this change, `CommunityPrivacy.PRIVATE` only removed a community from discovery.
`join()` never checked privacy, so **anyone holding the community id could join a private
community outright** and then read its `COMMUNITY`-visibility posts. "Private" hid the
community from browse but did not actually restrict membership — an access-control hole,
not a missing nicety.

---

## 3. Membership model

Membership lives in `community_members` with a `status` of type `MemberStatus`:

| Status | Meaning |
|---|---|
| `ACTIVE` | A real member. Counted, listed, and served the community feed. |
| `PENDING` | Requested to join a PRIVATE community; awaiting approval. **Not yet a member.** |
| `LEFT` | Left voluntarily. |
| `REMOVED` | Removed by a manager, or a join request that was rejected. |

`PENDING` is the value added by this feature. No migration was needed: the `status`
column is a constraint-free `VARCHAR(50)` and the mapping is `@Enumerated(EnumType.STRING)`,
so the new enum value simply stores its own name.

**A pending request is not a membership.** This is enforced in several independent places,
so a non-approved user can never see private content:

- `memberCount` and `joinedAt` do not move until approval.
- `roleIn(...)` returns a role only for `ACTIVE` members, so a pending user has no role.
- `CommunityMemberRepository.findActiveCommunityIds(...)` filters to `ACTIVE`, so the
  personalised feed (`COMMUNITY`-visibility posts) never reaches a pending user.
- The member list (`findMembers(..., ACTIVE)`) excludes them.

---

## 4. Endpoints

Base path: `/api/v1/community/communities`.

| Method | Path | Auth | Purpose |
|---|---|---|---|
| POST | `/{id}/join` | any authenticated | Join (PUBLIC) or request to join (PRIVATE). Idempotent. |
| GET | `/{id}/join-requests` | owner/admin | List pending requests (paginated). |
| POST | `/{id}/join-requests/{userId}/approve` | owner/admin | Approve a pending request → member. |
| DELETE | `/{id}/join-requests/{userId}` | owner/admin | Reject a pending request. |

**Authorization:** the three management endpoints require the caller to be `OWNER` or
`ADMIN` of that community. A non-manager receives **404 not-found**, not 403 — the API does
not confirm that a community it will not let you manage exists. This matches the
"ownership-scoped lookups return not found" convention used throughout the module.

---

## 5. Behaviour

### Join (`POST /{id}/join`)

Response is a `CommunityResponse` carrying two relevant flags:

- `joined` — true when the caller is an `ACTIVE` member.
- `requestPending` — true when the caller has a `PENDING` request awaiting approval.

| Community | Existing membership | Result |
|---|---|---|
| PUBLIC | none / LEFT / REMOVED | `ACTIVE` immediately, `memberCount +1`, `joined=true`. |
| PUBLIC | already ACTIVE | Idempotent no-op, returns current state. |
| PRIVATE | none / LEFT / REMOVED | `PENDING` request created, count unchanged, `requestPending=true`. |
| PRIVATE | already PENDING | Idempotent — no duplicate, still `requestPending=true`. |
| PRIVATE | already ACTIVE | Idempotent no-op. |

Rejoining reuses the existing row — `UNIQUE(community_id, user_id)` forbids a second one.

### Approve (`POST /{id}/join-requests/{userId}/approve`)

Sets the pending member to `ACTIVE`, stamps `joinedAt`, and increments `memberCount`
(the count moves only now — a pending request never incremented it). Returns the new
member as a `CommunityMemberResponse`. If there is no pending request for that user, 404.

### Reject (`DELETE /{id}/join-requests/{userId}`)

Sets the request to `REMOVED` without touching `memberCount`. The row is kept as the record
that a request was declined, and the unique constraint forbids a fresh row anyway. The user
**may request again later**, which re-opens a `PENDING` request through the rejoin path.

---

## 6. Client rendering

`CommunityResponse.requestPending` lets the Join button render three states without a
second request:

- `joined == true` → **Leave**
- `requestPending == true` → **Requested** (disabled)
- otherwise → **Join**

`GET /{id}/join-requests` returns each requester as `CommunityMemberResponse`; the
`user.userId` field is what the owner passes to approve/reject.

---

## 7. Known limits / not built

- **Owner request notification delivered (2026-09-09).** A new or reopened PRIVATE join
  request notifies the community owner after the pending membership row is saved. The
  notification includes the requester/community details, `COMMUNITY + communityId` reference,
  and a recipient-scoped dedupe key. Existing pending requests and public joins do not create
  duplicates. Approval notification to the requester is not built yet.
- **Rejection is not a block.** A rejected user can request again. A sticky blocklist would
  be a separate feature.
- **Role promotion and ownership transfer are implemented** — see `COMMUNITY_ROLES.md`.
  The remaining join-flow limits are the missing approval notification and the fact that
  rejection is not a sticky block.
