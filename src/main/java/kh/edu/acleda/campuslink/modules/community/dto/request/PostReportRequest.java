package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * @param reason free text rather than an enum for now: the moderation vocabulary is not
 *               settled, and guessing it here would bake the wrong one in. Mirrors
 *               {@code DiscussionReportRequest}.
 */
public record PostReportRequest(
    @NotBlank @Size(max = 80) String reason,
    @Size(max = 2000) String details
) {
}
