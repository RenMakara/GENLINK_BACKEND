package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.StoryMediaType;
import kh.edu.acleda.campuslink.modules.community.enums.StoryReplyPermission;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;

/** Posts a story. */
public record StoryCreateRequest(
    String mediaUrl,
    @NotNull StoryMediaType mediaType,
    String caption,
    Visibility visibility,
    StoryReplyPermission replyPermission,
    Boolean allowReactions,
    UUID mediaId
) {
    public StoryCreateRequest(String mediaUrl, StoryMediaType mediaType, String caption,
                              Visibility visibility, StoryReplyPermission replyPermission,
                              Boolean allowReactions) {
        this(mediaUrl, mediaType, caption, visibility, replyPermission, allowReactions, null);
    }

    public StoryCreateRequest(String mediaUrl, StoryMediaType mediaType, String caption) {
        this(mediaUrl, mediaType, caption, Visibility.PUBLIC, StoryReplyPermission.EVERYONE, true, null);
    }

    @AssertTrue(message = "Story media or mediaId is required")
    public boolean hasMedia() {
        return mediaId != null || (mediaUrl != null && !mediaUrl.isBlank());
    }
}
