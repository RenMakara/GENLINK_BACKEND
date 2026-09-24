package kh.edu.acleda.campuslink.modules.admin.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Input for assigning one catalogue badge to a bounded set of users. */
public record AdminBulkBadgeRequest(
    @NotNull
    UUID batchId,

    @NotEmpty
    @Size(max = 500, message = "BULK_LIMIT_EXCEEDED")
    List<@NotNull UUID> userIds,

    @NotNull
    UUID badgeId,

    @NotBlank
    @Size(min = 10, max = 2_000)
    String reason
) {
}
