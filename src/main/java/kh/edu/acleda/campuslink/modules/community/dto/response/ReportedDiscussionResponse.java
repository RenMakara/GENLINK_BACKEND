package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * A row in the admin forum-moderation queue.
 *
 * <p>Unlike {@link DiscussionResponse}, this <b>reveals the author even for anonymous
 * discussions</b>. That is the entire reason the author is stored on the row: moderation
 * needs accountability. {@code postedAnonymously} tells the reviewer it was anonymous to the
 * public, without hiding who wrote it from the moderator. This DTO is only ever returned on
 * the ADMIN-gated queue, never to ordinary users.
 */
public record ReportedDiscussionResponse(
    UUID discussionId,
    PostResponse.AuthorSummary author,
    boolean postedAnonymously,
    String title,
    String content,
    String category,
    int reportCount,
    Instant createdAt
) {
}
