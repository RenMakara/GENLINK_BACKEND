package kh.edu.acleda.campuslink.modules.community.dto.response;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    String videoUrl,
    PostType postType,
    Visibility visibility,
    Language language,
    Integer likeCount,
    Integer commentCount,
    Integer shareCount,
    boolean likedByMe,
    boolean savedByMe,
    List<String> hashtags,
    PollResponse poll,
    Instant createdAt,
    boolean repostedByMe,
    String repostedBy,
    String repostCaption,
    UUID reposterId,
    UUID originalPostId,
    String location,
    String linkUrl,
    String linkPreviewTitle,
    String linkPreviewDomain,
    UUID eventId,
    List<UUID> imageMediaIds,
    UUID videoMediaId
) {

    /** Alias used by the mobile community model. */
    @JsonProperty("repostCount")
    public Integer repostCount() {
        return shareCount;
    }

    /**
     * Backward-compatible constructor for existing callers and tests omitting videoUrl.
     */
    public PostResponse(
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
        PollResponse poll,
        Instant createdAt
    ) {
        this(postId, author, contentText, imageUrl, null, postType, visibility,
            language, likeCount, commentCount, null, likedByMe, savedByMe, hashtags, poll,
            createdAt, false, null, null, null, null, null, null, null, null, null, List.of(), null);
    }

    /** Backward-compatible constructor for callers that already provide videoUrl. */
    public PostResponse(
        UUID postId,
        AuthorSummary author,
        String contentText,
        String imageUrl,
        String videoUrl,
        PostType postType,
        Visibility visibility,
        Language language,
        Integer likeCount,
        Integer commentCount,
        boolean likedByMe,
        boolean savedByMe,
        List<String> hashtags,
        PollResponse poll,
        Instant createdAt
    ) {
        this(postId, author, contentText, imageUrl, videoUrl, postType, visibility,
            language, likeCount, commentCount, null, likedByMe, savedByMe, hashtags, poll,
            createdAt, false, null, null, null, null, null, null, null, null, null, List.of(), null);
    }

    /** Public-safe author fields only: no email, no phone. */
    public record AuthorSummary(
        UUID userId,
        String fullName,
        String username,
        String avatarUrl
    ) {}
}
