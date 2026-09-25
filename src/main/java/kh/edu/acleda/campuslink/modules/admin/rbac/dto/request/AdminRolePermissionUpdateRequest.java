package kh.edu.acleda.campuslink.modules.admin.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

/** Whole-set role permission replacement request. */
public record AdminRolePermissionUpdateRequest(
    @NotNull @Size(max = 40) List<@NotBlank @Size(max = 64) String> permissions,
    @NotBlank @Size(min = 10, max = 2000) String reason
) {
}
