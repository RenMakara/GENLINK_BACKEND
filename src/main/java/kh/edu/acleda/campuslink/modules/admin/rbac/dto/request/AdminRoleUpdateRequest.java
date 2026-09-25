package kh.edu.acleda.campuslink.modules.admin.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Editable role metadata and the required high-tier audit reason. */
public record AdminRoleUpdateRequest(
    @NotBlank @Size(max = 80) String name,
    @Size(max = 255) String description,
    @NotBlank @Size(min = 10, max = 2000) String reason
) {
}
