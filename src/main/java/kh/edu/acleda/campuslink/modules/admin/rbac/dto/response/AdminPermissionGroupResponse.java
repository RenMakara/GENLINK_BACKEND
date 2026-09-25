package kh.edu.acleda.campuslink.modules.admin.rbac.dto.response;

import java.util.List;

/** Permission catalogue entries grouped by their resource prefix. */
public record AdminPermissionGroupResponse(
    String resource,
    List<AdminPermissionResponse> permissions
) {

    public AdminPermissionGroupResponse {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
