package kh.edu.acleda.campuslink.modules.admin.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** High-tier input for a partial-success bulk ban operation. */
public record AdminBulkBanRequest(
    @NotNull
    UUID batchId,

    @NotEmpty
    @Size(max = 500, message = "BULK_LIMIT_EXCEEDED")
    List<@NotNull UUID> userIds,

    @NotBlank
    @Size(min = 10, max = 2_000)
    String reason
) {
}
