package kh.edu.acleda.campuslink.modules.admin.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Updates localized text while keeping the template key stable. */
public record AdminNotificationTemplateUpdateRequest(
    @NotBlank
    @Size(max = 180)
    String titleEn,

    @Size(max = 180)
    String titleKm,

    @NotBlank
    @Size(max = 4_000)
    String messageEn,

    @Size(max = 4_000)
    String messageKm,

    @NotBlank
    @Size(max = 500)
    String reason
) {
}
