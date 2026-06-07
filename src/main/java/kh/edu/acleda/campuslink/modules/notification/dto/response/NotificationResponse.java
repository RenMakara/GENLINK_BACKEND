package kh.edu.acleda.campuslink.modules.notification.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import kh.edu.acleda.campuslink.shared.enums.Language;

public record NotificationResponse(
    UUID notificationId,
    String title,
    String message,
    Language language,
    String type,
    Boolean readStatus,
    LocalDateTime readAt
) {
}
