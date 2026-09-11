package kh.edu.acleda.campuslink.modules.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationDevicePlatform;

public record NotificationDeviceRegistrationRequest(
    @NotNull(message = "platform is required") NotificationDevicePlatform platform,
    @NotBlank(message = "token is required")
    @Size(max = 4096, message = "token is too long") String token
) {
}
