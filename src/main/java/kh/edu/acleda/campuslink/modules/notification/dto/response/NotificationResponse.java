package kh.edu.acleda.campuslink.modules.notification.dto.response;

import java.time.Instant;
import java.time.LocalDateTime;
import java.util.UUID;
import kh.edu.acleda.campuslink.shared.enums.Language;

public record NotificationResponse(
    UUID notificationId,
    String title,
    String message,
    Language language,
    String type,
    String referenceType,
    UUID referenceId,
    Boolean readStatus,
    LocalDateTime readAt,
    Instant createdAt,
    String dedupeKey
) {
    public NotificationResponse(
        UUID notificationId,
        String title,
        String message,
        Language language,
        String type,
        String referenceType,
        UUID referenceId,
        Boolean readStatus,
        LocalDateTime readAt
    ) {
        this(notificationId, title, message, language, type, referenceType, referenceId, readStatus, readAt, null, null);
    }

    public NotificationResponse(
        UUID notificationId,
        String title,
        String message,
        Language language,
        String type,
        String referenceType,
        UUID referenceId,
        Boolean readStatus,
        LocalDateTime readAt,
        Instant createdAt
    ) {
        this(notificationId, title, message, language, type, referenceType, referenceId, readStatus, readAt, createdAt, null);
    }
}
