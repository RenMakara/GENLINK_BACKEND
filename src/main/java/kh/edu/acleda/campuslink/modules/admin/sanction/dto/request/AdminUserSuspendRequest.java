package kh.edu.acleda.campuslink.modules.admin.sanction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/** Medium-tier input for a fixed-duration suspension. */
public record AdminUserSuspendRequest(
    @NotBlank
    @Size(min = 10, max = 2_000)
    String reason,

    @NotNull
    @Positive
    Integer durationHours
) {
}
