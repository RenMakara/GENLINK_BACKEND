package kh.edu.acleda.campuslink.modules.admin.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** Creates a broadcast-only template in the reserved {@code ADMIN_} namespace. */
public record AdminNotificationTemplateCreateRequest(
    @NotBlank
    @Size(max = 120)
    @Pattern(regexp = "ADMIN_[A-Z0-9]+(?:[_.-][A-Z0-9]+)*")
    String templateKey,

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
