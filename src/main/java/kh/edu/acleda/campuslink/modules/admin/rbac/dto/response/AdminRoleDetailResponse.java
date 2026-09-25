package kh.edu.acleda.campuslink.modules.admin.rbac.dto.response;

import java.util.List;
import java.util.UUID;

/** Role projection used by the permission editor. */
public record AdminRoleDetailResponse(
    UUID roleId,
    String code,
    String name,
    String description,
    boolean isSystem,
    long adminCount,
    List<String> permissions
) {

    public AdminRoleDetailResponse {
        permissions = permissions == null ? List.of() : List.copyOf(permissions);
    }
}
