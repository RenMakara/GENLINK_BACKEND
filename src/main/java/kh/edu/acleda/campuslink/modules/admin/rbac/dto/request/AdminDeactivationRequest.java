package kh.edu.acleda.campuslink.modules.admin.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Revokes an admin profile while preserving the linked user account. */
public record AdminDeactivationRequest(
    @NotBlank @Size(min = 10, max = 2000) String reason
) {
}
