package kh.edu.acleda.campuslink.modules.security.dto.response;

import java.time.Instant;
import java.util.UUID;

/**
 * One active sign-in (a live refresh token). The token hash itself is never exposed.
 */
public record SessionResponse(
    UUID sessionId,
    String deviceInfo,
    String ipAddress,
    String userAgent,
    Instant createdAt,
    Instant lastUsedAt,
    Instant expiresAt
) {
}
