package kh.edu.acleda.campuslink.modules.admin.sanction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Common medium-tier input for Ban and Unban. */
public record AdminUserSanctionRequest(
    @NotBlank
    @Size(min = 10, max = 2_000)
    String reason
) {
}
