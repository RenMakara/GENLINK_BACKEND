package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * A row in a moderator's reported-posts queue: the post's essentials plus how many distinct
 * people have flagged it. Deliberately not a full PostResponse - the queue does not need
 * likedByMe / savedByMe / hashtags, and skipping them avoids per-row hydration queries.
 */
public record ReportedPostResponse(
    UUID postId,
    PostResponse.AuthorSummary author,
    String contentText,
    String imageUrl,
    int reportCount,
    Instant createdAt
) {
}
