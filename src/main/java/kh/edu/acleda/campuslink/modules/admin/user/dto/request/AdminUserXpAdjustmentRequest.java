package kh.edu.acleda.campuslink.modules.admin.user.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** Signed XP adjustment and the required administrative reason. */
public record AdminUserXpAdjustmentRequest(
    @NotNull Integer amount,

    @NotBlank
    @Size(min = 10, max = 2_000)
    String reason
) {
}
