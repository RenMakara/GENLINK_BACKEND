package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.PostType;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.shared.enums.Language;

/**
 * A feed row, renderable without a follow-up call.
 *
 * @param likedByMe resolved server-side; without it the client would fire one request
 *                  per post just to colour the like button
 * @param savedByMe same, for the save toggle
 */
public record PostResponse(
    UUID postId,
    AuthorSummary author,
    String contentText,
    String imageUrl,
    PostType postType,
    Visibility visibility,
    Language language,
    Integer likeCount,
    Integer commentCount,
    boolean likedByMe,
    boolean savedByMe,
    List<String> hashtags,
    Instant createdAt
) {

    /** Public-safe author fields only: no email, no phone. */
    public record AuthorSummary(
        UUID userId,
        String fullName,
        String username,
        String avatarUrl
    ) {}
}
