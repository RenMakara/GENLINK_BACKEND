package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.shared.enums.Language;

/**
 * Creates a poll, which is a {@code POLL} post carrying a question and its options.
 *
 * @param caption     optional free text shown as the post body; the question lives in
 *                    {@code question}, so this can be left blank.
 * @param options     2-6 non-blank choices. Single-choice voting is enforced by a unique
 *                    (poll, user) row, so there is no need to cap selections here.
 * @param communityId optional; set it to post the poll into a community you are in.
 */
public record PollCreateRequest(
    String caption,
    @NotBlank String question,
    @NotNull @Size(min = 2, max = 6) List<@NotBlank String> options,
    Visibility visibility,
    Language language,
    UUID communityId
) {
}
