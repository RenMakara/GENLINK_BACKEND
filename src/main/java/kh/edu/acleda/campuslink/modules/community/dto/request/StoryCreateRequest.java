package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import kh.edu.acleda.campuslink.modules.community.enums.StoryMediaType;

/**
 * Posts a story. The media is uploaded first via the existing presign/confirm flow;
 * {@code mediaUrl} is the resulting URL. Stories expire 24h after creation.
 */
public record StoryCreateRequest(
    @NotBlank String mediaUrl,
    @NotNull StoryMediaType mediaType,
    String caption
) {
}
