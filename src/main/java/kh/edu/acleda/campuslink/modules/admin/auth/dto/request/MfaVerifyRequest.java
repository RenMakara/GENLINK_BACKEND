package kh.edu.acleda.campuslink.modules.admin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

/** The second factor: either a 6-digit TOTP code or one of the ten recovery codes. */
public record MfaVerifyRequest(

    @NotBlank(message = "Code is required")
    @Schema(description = "A 6-digit TOTP code, or one of the ten recovery codes (ABCDE-FGHJK)", example = "123456")
    String code
) {
}
