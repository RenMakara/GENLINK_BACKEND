package kh.edu.acleda.campuslink.modules.admin.sanction.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionSeverity;

/** Bilingual, recorded-only warning issued from the Admin portal. */
public record AdminUserWarningRequest(
    @NotBlank
    @Size(min = 10, max = 2_000)
    String reason,

    @NotNull
    SanctionSeverity severity,

    @NotBlank
    @Size(max = 2_000)
    String messageEn,

    @NotBlank
    @Size(max = 2_000)
    String messageKm
) {
}
