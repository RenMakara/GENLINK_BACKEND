package kh.edu.acleda.campuslink.modules.security.dto.response;

import java.time.Instant;
import java.util.UUID;

public record SecurityActivityResponse(
    UUID activityId,
    String eventType,
    String status,
    Boolean success,
    String ipAddress,
    String userAgent,
    String failureReason,
    Instant occurredAt
) {
}
