package kh.edu.acleda.campuslink.modules.notification.dto.request;

import jakarta.validation.constraints.NotNull;

public record NotificationPreferenceUpdateRequest(
    @NotNull(message = "enabled is required") Boolean enabled
) {
}
