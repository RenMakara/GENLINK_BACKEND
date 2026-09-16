package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.AssertTrue;
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
    UUID communityId
) {

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
        this(contentText, imageUrl, null, postType, visibility, language, communityId);
    }

    @AssertTrue(message = "Post must have content text, an image, or a video")
    public boolean hasContent() {
        boolean hasText = contentText != null && !contentText.isBlank();
        boolean hasImage = imageUrl != null && !imageUrl.isBlank();
        boolean hasVideo = videoUrl != null && !videoUrl.isBlank();
        return hasText || hasImage || hasVideo;
    }

    @AssertTrue(message = "Text posts must have non-blank content text")
    public boolean isValidForPostType() {
        if (postType == PostType.TEXT) {
            return contentText != null && !contentText.isBlank();
        }
        if (postType == PostType.VIDEO) {
            return (videoUrl != null && !videoUrl.isBlank()) || (imageUrl != null && !imageUrl.isBlank());
        }
        return true;
    }
}
