package kh.edu.acleda.campuslink.modules.admin.user.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Safe active-session projection for the admin user detail page. */
public record AdminUserSessionResponse(
    UUID sessionId,
    String deviceInfo,
    String ipAddress,
    String userAgent,
    Instant createdAt,
    Instant lastUsedAt,
    Instant expiresAt
) {
}
