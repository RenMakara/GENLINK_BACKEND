package kh.edu.acleda.campuslink.modules.admin.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Changes one admin profile's role and records the high-tier reason. */
public record AdminRoleChangeRequest(
    @NotNull UUID roleId,
    @NotBlank @Size(min = 10, max = 2000) String reason
) {
}
