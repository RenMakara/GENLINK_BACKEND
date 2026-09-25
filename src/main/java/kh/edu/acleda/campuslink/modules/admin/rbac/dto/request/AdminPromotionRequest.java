package kh.edu.acleda.campuslink.modules.admin.rbac.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.util.UUID;

/** Promotes an existing, verified user into one admin role. */
public record AdminPromotionRequest(
    @NotNull UUID userId,
    @NotNull UUID roleId,
    @NotBlank @Size(min = 10, max = 2000) String reason
) {
}
