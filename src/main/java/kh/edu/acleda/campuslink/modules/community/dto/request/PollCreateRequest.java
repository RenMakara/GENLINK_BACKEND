package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.shared.enums.Language;

/** Creates a poll, which is a {@code POLL} post carrying a question and its options. */
public record PollCreateRequest(
    String caption,
    @NotBlank String question,
    @NotNull @Size(min = 2, max = 6) List<@NotBlank String> options,
    Visibility visibility,
    Language language,
    UUID communityId,
    Integer durationDays,
    String location,
    String linkUrl,
    String linkPreviewTitle,
    String linkPreviewDomain,
    UUID attachedEventId,
    String imageUrl,
    String videoUrl,
    List<UUID> imageMediaIds,
    UUID videoMediaId
) {
    public PollCreateRequest(String caption, String question, List<String> options,
                             Visibility visibility, Language language, UUID communityId) {
        this(caption, question, options, visibility, language, communityId,
            null, null, null, null, null, null, null, null, null, null);
    }
}
