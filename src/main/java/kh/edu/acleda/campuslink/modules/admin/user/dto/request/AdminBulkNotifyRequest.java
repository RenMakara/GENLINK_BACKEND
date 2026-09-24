package kh.edu.acleda.campuslink.modules.admin.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Input for a bounded, bilingual in-app notification broadcast to selected users. */
public record AdminBulkNotifyRequest(
    @NotNull
    UUID batchId,

    @NotEmpty
    @Size(max = 500, message = "BULK_LIMIT_EXCEEDED")
    List<@NotNull UUID> userIds,

    @NotBlank
    @Size(max = 120)
    @Pattern(regexp = "ADMIN_[A-Z0-9]+(?:[_.-][A-Z0-9]+)*")
    String templateKey,

    @Size(max = 20)
    Map<String, String> parameters,

    @NotBlank
    @Size(min = 10, max = 500)
    String reason
) {
}
