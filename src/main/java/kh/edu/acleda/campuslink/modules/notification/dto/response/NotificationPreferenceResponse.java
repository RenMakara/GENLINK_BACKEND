package kh.edu.acleda.campuslink.modules.notification.dto.response;

import java.util.UUID;

public record NotificationPreferenceResponse(
    UUID preferenceId,
    String notificationType,
    Boolean enabled
) {
}
