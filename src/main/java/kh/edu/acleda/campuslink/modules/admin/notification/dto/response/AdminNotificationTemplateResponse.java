package kh.edu.acleda.campuslink.modules.admin.notification.dto.response;

import java.time.Instant;
import java.util.UUID;

/** Public Admin representation of a broadcast-only notification template. */
public record AdminNotificationTemplateResponse(
    UUID id,
    String templateKey,
    String titleEn,
    String titleKm,
    String messageEn,
    String messageKm,
    Instant createdAt,
    Instant updatedAt
) {
}
