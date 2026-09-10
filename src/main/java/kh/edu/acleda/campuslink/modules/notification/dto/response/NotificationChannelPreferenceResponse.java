package kh.edu.acleda.campuslink.modules.notification.dto.response;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;

public record NotificationChannelPreferenceResponse(
    UUID preferenceId,
    NotificationChannel channel,
    Boolean enabled
) {
}
