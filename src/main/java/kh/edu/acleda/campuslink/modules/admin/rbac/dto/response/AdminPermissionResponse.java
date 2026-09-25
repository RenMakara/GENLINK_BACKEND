package kh.edu.acleda.campuslink.modules.admin.rbac.dto.response;

/** One entry in the permission catalogue. */
public record AdminPermissionResponse(
    String code,
    String resource,
    String action,
    String description
) {
}
