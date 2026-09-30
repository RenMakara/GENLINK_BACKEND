package kh.edu.acleda.campuslink.modules.community.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** @param reason free text rather than an enum for now: the moderation vocabulary is not */
public record PostReportRequest(
    @NotBlank @Size(max = 80) String reason,
    @Size(max = 2000) String details
) {
}
