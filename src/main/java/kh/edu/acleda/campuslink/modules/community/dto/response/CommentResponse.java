package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.shared.enums.Language;

public record CommentResponse(
    UUID commentId,
    PostResponse.AuthorSummary author,
    String content,
    UUID parentCommentId,
    Language language,
    Integer likeCount,
    boolean mine,
    Instant createdAt
) {
}
