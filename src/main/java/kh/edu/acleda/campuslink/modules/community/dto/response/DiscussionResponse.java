package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.shared.enums.Language;

/**
 * @param author       null when the discussion is anonymous. The author id is still
 *                     stored on the row for moderation - it is withheld here, not
 *                     discarded. This DTO is the only thing standing between an
 *                     anonymous post and its writer, so nothing else may carry the
 *                     user through.
 * @param mine         true only for the author's own request. Safe on an anonymous
 *                     discussion: it tells the writer something they already know,
 *                     and is false for everyone else.
 * @param reactedByMe  resolved server-side, so a list does not fire a request per row
 */
public record DiscussionResponse(
    UUID discussionId,
    PostResponse.AuthorSummary author,
    String title,
    String content,
    String category,
    boolean anonymous,
    Language language,
    Integer reactionCount,
    Integer commentCount,
    boolean reactedByMe,
    boolean mine,
    Instant createdAt
) {
}
