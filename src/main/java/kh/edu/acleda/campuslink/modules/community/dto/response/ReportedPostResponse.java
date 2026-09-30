package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;

/** A row in a moderator's reported-posts queue: the post's essentials plus how many distinct people have flagged it. */
public record ReportedPostResponse(
    UUID postId,
    PostResponse.AuthorSummary author,
    String contentText,
    String imageUrl,
    String videoUrl,
    int reportCount,
    Instant createdAt
) {

    /**
     * Backward-compatible constructor for existing callers omitting videoUrl.
     */
    public ReportedPostResponse(
        UUID postId,
        PostResponse.AuthorSummary author,
        String contentText,
        String imageUrl,
        int reportCount,
        Instant createdAt
    ) {
        this(postId, author, contentText, imageUrl, null, reportCount, createdAt);
    }
}
