package kh.edu.acleda.campuslink.modules.notification.dto.response;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationDevicePlatform;

public record NotificationDeviceResponse(
    UUID deviceId,
    NotificationDevicePlatform platform,
    Boolean active,
    Instant lastSeenAt
) {
}
