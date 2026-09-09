package kh.edu.acleda.campuslink.modules.admin.notification.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** HTTP input for one bounded, audited in-app broadcast. */
public record AdminBroadcastRequest(
    @NotBlank
    @Size(max = 120)
    @Pattern(regexp = "ADMIN_[A-Z0-9]+(?:[_.-][A-Z0-9]+)*")
    String templateKey,

    @NotNull
    AdminBroadcastAudience audience,

    @Size(max = 500)
    List<@NotNull UUID> recipientUserIds,

    @Size(max = 20)
    Map<String, String> parameters,

    @NotBlank
    @Size(max = 500)
    String reason
) {
}
