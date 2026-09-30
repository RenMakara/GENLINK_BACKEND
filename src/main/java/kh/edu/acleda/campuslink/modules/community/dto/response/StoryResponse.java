package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.StoryMediaType;
import kh.edu.acleda.campuslink.modules.community.enums.StoryReplyPermission;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;

/** One story rendered for a viewer. */
public record StoryResponse(
    UUID storyId,
    StoryMediaType mediaType,
    String mediaUrl,
    String caption,
    boolean seen,
    Instant createdAt,
    Instant expiresAt,
    Visibility visibility,
    StoryReplyPermission replyPermission,
    boolean allowReactions,
    int reactionCount,
    boolean reactedByMe
) {
    public StoryResponse(
        UUID storyId,
        StoryMediaType mediaType,
        String mediaUrl,
        String caption,
        boolean seen,
        Instant createdAt,
        Instant expiresAt
    ) {
        this(storyId, mediaType, mediaUrl, caption, seen, createdAt, expiresAt,
            Visibility.PUBLIC, StoryReplyPermission.EVERYONE, true, 0, false);
    }

    /** Backward-compatible constructor for callers that do not yet provide reaction data. */
    public StoryResponse(
        UUID storyId,
        StoryMediaType mediaType,
        String mediaUrl,
        String caption,
        boolean seen,
        Instant createdAt,
        Instant expiresAt,
        Visibility visibility,
        StoryReplyPermission replyPermission,
        boolean allowReactions
    ) {
        this(storyId, mediaType, mediaUrl, caption, seen, createdAt, expiresAt,
            visibility, replyPermission, allowReactions, 0, false);
    }
}
