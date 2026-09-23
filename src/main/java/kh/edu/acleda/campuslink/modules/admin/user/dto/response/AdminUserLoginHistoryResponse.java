package kh.edu.acleda.campuslink.modules.admin.user.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Safe security-event projection for the admin user's login-history tab. */
public record AdminUserLoginHistoryResponse(
    UUID auditId,
    String eventType,
    boolean success,
    String failureReason,
    String ipAddress,
    String userAgent,
    Instant timestamp
) {
}
