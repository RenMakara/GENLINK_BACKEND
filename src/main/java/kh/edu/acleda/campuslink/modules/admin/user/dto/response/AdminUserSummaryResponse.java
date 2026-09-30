package kh.edu.acleda.campuslink.modules.admin.user.dto.response;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.WorkStatus;

/** Safe row projection for the admin user directory. */
public record AdminUserSummaryResponse(
    UUID userId,
    String fullName,
    String username,
    String email,
    String avatarUrl,
    boolean emailVerified,
    AccountStatus accountStatus,
    String adminRoleName,
    String universityName,
    WorkStatus workStatus,
    int level,
    int totalXp,
    Instant createdAt,
    Instant lastActiveAt
) {
}
