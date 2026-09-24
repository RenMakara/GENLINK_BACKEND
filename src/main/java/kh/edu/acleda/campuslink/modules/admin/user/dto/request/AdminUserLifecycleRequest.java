package kh.edu.acleda.campuslink.modules.admin.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Required audit reason for high-tier account lifecycle actions. */
public record AdminUserLifecycleRequest(
    @NotBlank @Size(min = 10, max = 2000) String reason
) {
}
