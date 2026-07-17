# Community Domain — Implementation Plan

Status: **Phases 0, 1, 2 and 4 done** (2026-07-17), except 4.4 (interest-based
suggestions). Phases 3 and 5 not started.
Created: 2026-07-17
Owner: Enat (Community domain, per Team Assignment v5)
Scope: backend only

---

## 1. Why this plan exists

Community is the largest domain in GenLink KH: 22 tables, more than everything
else combined. It cannot be built in one pass, and the order matters because the
early phases establish patterns the later ones copy.

## 2. Where Community stood before Phase 1

Measured 2026-07-17, not estimated. Kept as the record of what was found, and of
what "the endpoint exists" was worth. Posts, comments, likes and hashtags are now
real — see "Delivered" at the end. Events and Forum are still exactly as described
below.

| Thing | Count |
|---|---|
| Tables in the data dictionary | 22 |
| Entities | 21 |
| Repositories | 21 |
| Services | 3 — **all stubs** |
| Controllers | 3 |

**Community is effectively 0% implemented.** `PostService`, `DiscussionService`
and `EventService` exist with full CRUD controllers, but every method body is a
`TODO`.

The most dangerous of them:

```java
public PostResponse create(PostCreateRequest request) {
    // TODO: Implement Post creation using CurrentUserProvider and repository.
    return new PostResponse(null, request.contentText(), ...);
}
```

`POST /api/v1/community/posts` answers `success: true`, `"Post created"`, and
echoes the caller's own content back with a `null` id — while saving nothing.
`getMine()` always returns `[]`. A client developer would reasonably believe
posting works. Treat any "endpoint exists" signal in this module as unproven.

**18 of 21 entities have no service and no controller at all**: chat (4), polls
(3), comments, reactions, RSVP, opportunities, follows, hashtags, communities.

## 3. Two blockers to clear first

These are not features. Skip them and every endpoint below gets rewritten.

### 3.1 Nothing is paginated

`docs/API_STANDARDS.md` says "Use pagination". No endpoint in the codebase does.
Every `getMine()` returns an unbounded `List`. The only `Pageable` usages are in
`PostRepository`, `CommunityRepository` and `EventRsvpRepository`, all added for
`/mobile/home`. A feed without pagination will not survive the demo.

### 3.2 PostResponse has no author

```java
public record PostResponse(UUID postId, String contentText, String imageUrl,
    PostType postType, Visibility visibility, Language language,
    Integer likeCount, Integer commentCount) {}
```

No user id, no name, no avatar, no createdAt. **The feed cannot render who
posted.** Fix the DTO before building the feed on top of it.

---

## Phase 0 — Foundations

Do once. Everything after this depends on it.

- [x] **0.1** `PageResponse<T>` DTO: `content, page, size, totalElements, totalPages, hasNext`.
      Returned inside the existing `ApiResponse<T>`, so the envelope does not change.
      Adopt `Pageable` on every list endpoint from here on.
- [x] **0.2** Extend `PostResponse`: `author` (userId, fullName, avatarUrl),
      `createdAt`, `likedByMe`, `savedByMe`.
      `likedByMe` matters: without it the client fires one request per post to
      colour the like button.
- [x] **0.3** Confirm the counts rule. `Developer Notes v5` already says cached
      counts must be maintained transactionally: `likeCount`, `commentCount`,
      `shareCount`, `saveCount` on posts, `attendeeCount` on events,
      `memberCount`/`postCount` on communities. Update them in the same
      transaction as the write, never in a scheduled job.

---

## Phase 1 — Feed (the spine)

The single most visible surface in the app. Also unblocks `/mobile/home`, whose
`feedPreview` currently queries posts that can never exist.

- [x] **1.1** `PostServiceImpl` for real: create, update, delete.
      Ownership-scoped lookups (another user's id reads as not found, not
      forbidden). Soft delete via `deletedAt`.
- [x] **1.2** `GET /api/v1/community/posts/feed` — paginated, `deletedAt IS NULL`,
      visibility-filtered, author `JOIN FETCH`ed (never lazy-load per row).
- [x] **1.3** Likes: `POST/DELETE /api/v1/community/posts/{id}/like`.
      Idempotent — it backs a UI toggle, and `post_reactions` has
      `UNIQUE(post_id, user_id)`, so a double tap must not 500.
      Maintain `likeCount` in the same transaction.
- [x] **1.4** Comments: `POST/GET/DELETE /api/v1/community/posts/{id}/comments`.
      Paginated. Maintain `commentCount`. `post_comments.parent_comment_id`
      already exists, so replies are possible — keep threading one level deep.
- [x] **1.5** Hashtags: extract `#tags` from `contentText` on create, upsert
      `hashtags`, link `post_hashtags`, bump `usage_count`.
      `GET /api/v1/community/hashtags/trending`. Powers "Trending" and "Hashtags"
      in the Social Feed flow.
- [x] **1.6** Tests: ownership, soft delete, like idempotency, count accuracy,
      pagination bounds.

**Demoable after Phase 1:** post → see it in the feed → like → comment.

---

## Phase 2 — Events + RSVP — DONE

- [x] **2.1** `EventServiceImpl` for real: CRUD, ownership, soft delete.
- [x] **2.2** `GET /api/v1/community/events` — paginated, upcoming + category.
      **Free/paid filter not done** — the columns exist now, the filter does not.
- [x] **2.3** RSVP: `POST /events/{id}/rsvp`, `DELETE /events/{id}/rsvp`.
      Capacity check **before** payment (see the corrected paid-RSVP flow),
      `UNIQUE(event_id, user_id)`, maintain `attendeeCount`, award XP.
      **Organiser notification not done** — the notification module exists but is
      not wired to anything, so this would be the first caller.
- [x] **2.4** Paid events route through the demo wallet — simulation only. Never
      imply a real KHQR transfer.

Defer: QR check-in, map/location search.

---

## Phase 3 — Forum

- [ ] **3.1** `DiscussionServiceImpl` for real: CRUD, ownership, soft delete.
- [ ] **3.2** `ForumComment`: add, list (paginated), delete. Maintain `commentCount`.
- [ ] **3.3** Anonymous discussions: `is_anonymous` exists. The author id must
      still be stored for moderation, and withheld in the response.
- [ ] **3.4** Category filter, reactions, `report_count`.

Note: the dictionary calls this `discussion_comments`; the actual table is
`forum_comments`. Same thing, two names — see section "Dictionary drift".

---

## Phase 4 — Communities + Follows — DONE

Done ahead of Phases 2-3: it finishes what Phase 1 started. `Visibility` had three
values and only `PUBLIC` meant anything, because nothing could resolve "friends" or
"my communities". All three now work.

- [x] **4.1** Community CRUD, join/leave, `memberCount`, roles
      (OWNER/ADMIN/MODERATOR/MEMBER), `CommunityPrivacy`.
- [x] **4.2** Follow/unfollow, followers/following counts.
      A friend is a **mutual ACCEPTED follow** — already relied on by
      `getPublicProfile`.
- [x] **4.3** Feed personalisation: posts from followed users and joined
      communities. Enforce `FRIENDS` and `COMMUNITY` visibility properly.
- [ ] **4.4** Onboarding interests already exist — use them to seed suggestions.
      **Not done.** Suggestions rank by `memberCount` only. Interests are collected
      at onboarding and still drive nothing, so a Khmer-learning student and a
      startup founder see the same list.

---

## Phase 5 — Opportunities

- [ ] **5.1** `Opportunity` CRUD (admin/seeded), paginated list, filters by type
      and deadline.
- [ ] **5.2** Nothing else needed for saving: `saved_items` already covers
      `OPPORTUNITY`.

---

## Deferred on purpose

| Feature | Why |
|---|---|
| **Chat** | Rooms, members, messages, read receipts, presence **and WebSocket**. A project on its own. The app demos fine without it. It is the most detailed flow on the board, which makes cutting it feel wrong — cut it anyway unless Phases 1-3 land early. |
| **Polls** | `polls`, `poll_options`, `poll_votes` exist. `PostType.POLL` can wait. |
| **Stories** | No table, no entity. Would need a migration first. |
| **Skill showcase / Mentor matching** | No `skills`, no `mentors` table. Skill Showcase overlaps Portfolio, which is already built. |

---

## Dictionary drift to fix alongside

- `discussion_comments` (dictionary) vs `forum_comments` (actual table, 0009).
- `saved_opportunities` is in the dictionary with **no migration and no entity**.
  Never built, and now redundant — `saved_items` covers it. Delete it from the
  dictionary.

---

## Conventions every phase must follow

Pulled from `docs/CLAUDE.md`, `docs/API_STANDARDS.md`, `docs/DB_STANDARDS.md` and
the existing code, so the module stays consistent with Profile and Security.

1. UUID primary keys; entities extend `BaseEntity`.
2. Never expose entities — DTO in, DTO out.
3. `ApiResponse<T>` envelope; stable `errorCode` on failures.
4. LAZY relationships; `JOIN FETCH` where a list needs the relation.
5. Ownership-scoped lookups return **not found**, not forbidden — do not leak
   that another user's row exists.
6. Soft delete via `deletedAt`; readers filter it.
7. Cached counts updated in the same transaction as the write.
8. Idempotent toggles (like, save, RSVP) — they back UI switches.
9. Bilingual system content uses `_en`/`_km` columns + `LanguageResolver`.
   User content is stored as written with a `language` field.
10. Swagger `@Tag` on every controller.

---

## Suggested order

**Phase 0 + Phase 1, then stop and reassess.** That produces a working feed — the
most visible thing in the product — and proves the patterns (pagination, counts,
hydration, idempotency) that Phases 2-5 all copy. Reassessing after 1 is cheaper
than discovering a wrong pattern in Phase 4.

## Open questions

1. **Ownership.** Community is Enat's assigned area. Phases 1-5 are a large slice
   of their work — coordinate before starting.
2. **Chat.** Confirm it is deferred.
3. **Feed scope.** The FigJam feed mixes curated news, followed users, skills,
   events and leaderboard highlights. Phase 1 delivers posts only. Is that enough
   for the demo?

---

## Delivered — Phase 0 + Phase 1 (2026-07-17)

### Endpoints

| Method | Path |
|---|---|
| POST | `/api/v1/community/posts` |
| GET | `/api/v1/community/posts/feed` (paginated) |
| GET | `/api/v1/community/posts` (mine, paginated) |
| GET | `/api/v1/community/posts/{id}` |
| PUT | `/api/v1/community/posts/{id}` |
| DELETE | `/api/v1/community/posts/{id}` |
| POST/DELETE | `/api/v1/community/posts/{id}/like` |
| POST/GET | `/api/v1/community/posts/{postId}/comments` |
| GET | `/api/v1/community/comments/{id}/replies` |
| DELETE | `/api/v1/community/comments/{id}` |
| GET | `/api/v1/community/hashtags/trending` |

### Decisions worth keeping

- `PageResponse<T>` lives in `shared/response` and is returned inside the existing
  `ApiResponse<T>`. Spring's `Page` is not serialised directly: it leaks Pageable
  internals and its JSON shape is unstable across versions. **Phases 2-5 reuse this.**
- Feed hydration is batched: likes, saves and hashtags each cost one query per page,
  not per row. `PostServiceImplTest` pins this with `times(1)` verifications, so an
  N+1 reintroduced later fails the build.
- Counters use `UPDATE ... SET likeCount = likeCount + :delta` rather than
  read-modify-write, so two concurrent likes cannot both read 4 and both write 5.
- Like/unlike are idempotent — `post_reactions` has `UNIQUE(post_id, user_id)` and
  the endpoint backs a toggle.
- Comment threading is capped at one level: replying to a reply attaches to the same
  top-level parent, so the client never renders arbitrary nesting. A parent from a
  different post is rejected.
- Ownership-scoped lookups return **not found**, never forbidden.

### Two bugs fixed on the way

- `PostComment.parentComment` was `@JoinColumn(nullable = false)`, contradicting
  migration 0009. Under the test profile's `ddl-auto: create-drop` Hibernate would
  generate a NOT NULL column and reject **every top-level comment**, since only
  replies have a parent.
- The hashtag regex used `\p{L}`, which silently truncated Khmer tags. Khmer writes a
  syllable as a consonant plus vowel signs and coeng, which Unicode classifies as
  marks (`\p{M}`), not letters. `#បច្ចេកវិទ្យា` was being cut at the first vowel sign.
  Caught by `HashtagServiceImplTest`, which now pins Khmer extraction.

### Not done in Phase 1

- `posts.community_id` is not set on create. It belongs with Phase 4, when
  communities become joinable and `Visibility.COMMUNITY` can mean something.
- The feed serves `Visibility.PUBLIC` only. `FRIENDS` and `COMMUNITY` need the follow
  graph and memberships from Phase 4.
- Comment likes: `post_comments.like_count` exists and is returned, but nothing
  increments it yet.

---

## Delivered — Phase 4 (2026-07-17)

Taken before Phases 2-3 because it completes Phase 1: the feed was a public
timeline, not a social product.

### Endpoints

| Method | Path |
|---|---|
| POST | `/api/v1/community/communities` |
| GET | `/api/v1/community/communities` (discover, paginated) |
| GET | `/api/v1/community/communities/mine` |
| GET | `/api/v1/community/communities/{slug}` |
| PUT/DELETE | `/api/v1/community/communities/{id}` |
| POST | `/api/v1/community/communities/{id}/join` |
| DELETE | `/api/v1/community/communities/{id}/leave` |
| GET | `/api/v1/community/communities/{id}/members` |
| GET | `/api/v1/community/posts/community/{id}` |
| POST/DELETE | `/api/v1/community/users/{id}/follow` |
| GET | `/api/v1/community/users/{id}/follow-stats`, `/followers`, `/following` |
| GET | `/api/v1/community/follow-requests` |
| POST | `/api/v1/community/follow-requests/{id}/accept` |
| DELETE | `/api/v1/community/follow-requests/{id}` |

### What this unlocked

- **The feed is now personalised.** `findVisibleFeed` serves public posts, the
  viewer's own, FRIENDS posts from mutual follows, and COMMUNITY posts from
  communities they have joined. `Visibility` finally means something.
- `posts.community_id` is set on create — the column existed since 0015, unused.
- `/mobile/home`'s `recommendedCommunities` can return real rows.
- `follows` is no longer used by only `getPublicProfile`.

### Decisions worth keeping

- **Slugs are derived from the name, never sent by the client**, with a numeric
  suffix on collision. Khmer names normalise to nothing under NFKD, so they fall
  back to a generated slug rather than an empty one.
- **The slug is never regenerated on update.** It is a public URL; silently
  changing it breaks every link already shared.
- **The creator is the first member**, otherwise a community starts with an owner
  who is not in it and `memberCount` disagrees with `community_members`.
- **The OWNER cannot leave** — that would orphan the community with nobody able to
  administer it. Transfer ownership first (not built).
- **Rejoining reuses the membership row**, because `UNIQUE(community_id, user_id)`
  forbids a second one. Status moves LEFT -> ACTIVE.
- **A PRIVATE profile makes follows PENDING** and needs approval, mirroring how
  `getPublicProfile` hides a private profile. Everything else is ACCEPTED at once.
- **A friend is a mutual ACCEPTED follow** — one-way is a follower, not a friend.
- Empty `IN ()` is invalid SQL, so a viewer with no friends and no communities gets
  a placeholder id that matches nothing, and still sees the public feed.

### Known gaps

- **Ownership transfer does not exist**, so an owner can never leave their own
  community. Needs a `PUT /communities/{id}/members/{userId}/role` endpoint.
- **Role management is not exposed**: `CommunityRole` has ADMIN and MODERATOR but
  nothing can promote anyone. Only the creator is ever anything but MEMBER.
- **Private community membership is open**: `CommunityPrivacy.PRIVATE` hides a
  community from discovery, but `join` does not gate on it. Anyone with the id can
  still join. Follows gained a PENDING path; communities did not.
- `user_settings.allow_follow_request` is still unused — PENDING keys off
  `privacyProfile` instead.

---

## Delivered — Phase 2 (2026-07-17)

### Endpoints

| Method | Path |
|---|---|
| POST | `/api/v1/community/events` |
| GET | `/api/v1/community/events` (browse: published + upcoming, `?category=`) |
| GET | `/api/v1/community/events/mine` (includes drafts) |
| GET | `/api/v1/community/events/{id}` |
| PUT/DELETE | `/api/v1/community/events/{id}` |
| POST/DELETE | `/api/v1/community/events/{id}/rsvp` |
| GET | `/api/v1/community/events/{id}/attendees` |

### Migration 0018 — paid events did not exist

The Events flow has a Free/Paid toggle and a price. **`events` had neither column**,
not in migration 0009 and not in the data dictionary, so every event was implicitly
free and the paid RSVP path could not be built at all.

0018 adds `is_paid` and `price`, plus
`chk_events_paid_requires_price CHECK (is_paid = FALSE OR (price IS NOT NULL AND price > 0))`
— a paid event with no price would take a payment of nothing and still write a
wallet transaction.

### The ordering that matters

Capacity is checked **before** payment, matching the corrected paid-RSVP flow. The
other order takes someone's money and then tells them the event is full. Pinned by
`aFullPaidEventIsRejectedBeforeTheWalletIsTouched`.

### Decisions worth keeping

- Paid RSVP deducts the demo wallet and writes a `demo_transaction` of type
  `EVENT_PAYMENT`. `RsvpResponse.simulatedPayment` is true whenever money moved, so
  the client can label the screen a simulation. **Nothing here touches a real
  payment rail.**
- RSVP is idempotent: it backs a button and `(event_id, user_id)` is unique. A
  second tap does not double-count or re-award XP.
- Cancelling **does not refund** — the demo wallet has no refund flow, and inventing
  one would imply a payment rail that does not exist.
- Rejoining after cancelling reuses the RSVP row; the unique constraint forbids a
  second one.
- `attendeeCount` uses `UPDATE ... SET count = count + :delta`, so two people
  joining at once cannot both read 9 and both write 10 and overshoot capacity.
- Only PUBLISHED events are discoverable; a DRAFT belongs to its organiser alone.
  Past events are excluded from browse.
- A free event never touches the wallet. A free event never keeps a stale price.
- +10 XP per RSVP, written to `xp_history` and `users.total_xp`.
- `EventResponse` carries organizer, `myRsvpStatus` and `full`, so the list renders
  Join/Cancel/Full without a request per row. It previously had no organizer.

### Known gaps

- **No organiser notification.** The flow says the organiser is told when someone
  joins. The `notification` module exists but nothing calls it yet — this would be
  its first caller.
- **No free/paid filter** on browse, though the columns now exist.
- **No QR check-in.** `event_rsvps.checked_in` and `qr_code` exist, unused.
- **No refund on cancel**, deliberately.
- **No map/location search.** `latitude`/`longitude` exist, unused.
