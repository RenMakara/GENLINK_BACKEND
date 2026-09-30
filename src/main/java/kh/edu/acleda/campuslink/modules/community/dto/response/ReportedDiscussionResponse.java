package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A row in the admin forum-moderation queue. */
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
