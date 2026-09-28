package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.AssertTrue;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.PostType;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.shared.enums.Language;

/**
 * @param communityId optional. Set it to post into a community; required in practice
 *                    for visibility = COMMUNITY, which is otherwise visible to nobody.
 */
public record PostCreateRequest(
    String contentText,
    String imageUrl,
    String videoUrl,
    PostType postType,
    Visibility visibility,
    Language language,
    UUID communityId,
    List<UUID> imageMediaIds,
    UUID videoMediaId,
    String location,
    String linkUrl,
    String linkPreviewTitle,
    String linkPreviewDomain,
    UUID attachedEventId,
    String pollQuestion,
    List<String> pollOptions,
    Integer pollDurationDays,
    boolean replaceRichFields
) {

    public PostCreateRequest(String contentText, String imageUrl, String videoUrl,
                             PostType postType, Visibility visibility, Language language,
                             UUID communityId, List<UUID> imageMediaIds, UUID videoMediaId) {
        this(contentText, imageUrl, videoUrl, postType, visibility, language, communityId,
            imageMediaIds, videoMediaId, null, null, null, null, null, null, null, null, false);
    }

    public PostCreateRequest(String contentText, String imageUrl, String videoUrl,
                             PostType postType, Visibility visibility, Language language,
                             UUID communityId) {
        this(contentText, imageUrl, videoUrl, postType, visibility, language,
            communityId, null, null, null, null, null, null, null, null, null, null, false);
    }

    /**
     * Backward-compatible constructor for callers and tests that omit videoUrl.
     */
    public PostCreateRequest(
        String contentText,
        String imageUrl,
        PostType postType,
        Visibility visibility,
        Language language,
        UUID communityId
    ) {
        this(contentText, imageUrl, null, postType, visibility, language, communityId,
            null, null, null, null, null, null, null, null, null, null, false);
    }

    @AssertTrue(message = "Post must have content text, an image, or a video")
    public boolean hasContent() {
        boolean hasText = contentText != null && !contentText.isBlank();
        boolean hasImage = (imageUrl != null && !imageUrl.isBlank())
            || (imageMediaIds != null && !imageMediaIds.isEmpty());
        boolean hasVideo = (videoUrl != null && !videoUrl.isBlank()) || videoMediaId != null;
        boolean hasStructuredContent = (location != null && !location.isBlank())
            || (linkUrl != null && !linkUrl.isBlank()) || attachedEventId != null
            || (pollQuestion != null && !pollQuestion.isBlank());
        return hasText || hasImage || hasVideo || hasStructuredContent;
    }

    @AssertTrue(message = "Text posts must have non-blank content text")
    public boolean isValidForPostType() {
        if (postType == PostType.TEXT) {
            return (contentText != null && !contentText.isBlank())
                || (location != null && !location.isBlank())
                || (linkUrl != null && !linkUrl.isBlank())
                || attachedEventId != null;
        }
        if (postType == PostType.VIDEO) {
            return (videoUrl != null && !videoUrl.isBlank()) || videoMediaId != null
                || (imageUrl != null && !imageUrl.isBlank())
                || (imageMediaIds != null && !imageMediaIds.isEmpty());
        }
        return true;
    }
}
