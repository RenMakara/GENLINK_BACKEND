package kh.edu.acleda.campuslink.modules.productivity.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record MilestoneCreateRequest(
    @NotBlank @Size(max = 160) String title
) {
}
