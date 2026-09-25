package kh.edu.acleda.campuslink.modules.admin.rbac.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Safe admin-directory projection; credential material is intentionally absent. */
public record AdminStaffResponse(
    UUID adminId,
    UUID userId,
    String fullName,
    String username,
    String email,
    UUID roleId,
    String roleCode,
    String roleName,
    boolean active,
    boolean totpEnabled,
    boolean totpConfirmed,
    Instant lastLoginAt,
    Instant createdAt,
    Instant deletedAt
) {
}
