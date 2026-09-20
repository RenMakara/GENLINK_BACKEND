package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.StoryMediaType;
import kh.edu.acleda.campuslink.modules.community.enums.StoryReplyPermission;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;

/**
 * One story rendered for a viewer.
 *
 * @param seen whether the viewer has already seen this story; the viewer's own stories
 *             always read as seen.
 */
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
    boolean allowReactions
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
            Visibility.PUBLIC, StoryReplyPermission.EVERYONE, true);
    }
}
