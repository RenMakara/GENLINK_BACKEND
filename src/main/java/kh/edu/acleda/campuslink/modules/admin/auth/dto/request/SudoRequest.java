package kh.edu.acleda.campuslink.modules.admin.auth.dto.request;

import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

/** Re-authentication for a high-tier action: a password or a fresh TOTP code (SSOT §10.7). */
public record SudoRequest(

    @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
    @Schema(description = "A current 6-digit TOTP code. Preferred over the password.", example = "123456")
    String code,

    @Schema(description = "Account password, when a code is not available", example = "correct-horse-battery-staple")
    String password
) {
}
