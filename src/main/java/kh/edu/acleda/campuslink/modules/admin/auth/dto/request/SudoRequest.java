package kh.edu.acleda.campuslink.modules.admin.auth.dto.request;

import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Re-authentication for a high-tier action: a password or a fresh TOTP code (SSOT §10.7).
 *
 * <p>Both fields are optional individually because either factor is accepted; the service
 * rejects a request carrying neither. Expressed as two nullable fields rather than one "secret"
 * so the API says which factors exist, and so a 6-digit password cannot be silently read as a
 * TOTP code.
 *
 * @param code a current 6-digit TOTP code. Preferred over the password: a password can be
 *     pulled from a manager on the very machine this control defends against, whereas a code
 *     needs the phone.
 */
public record SudoRequest(

    @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
    @Schema(description = "A current 6-digit TOTP code. Preferred over the password.", example = "123456")
    String code,

    @Schema(description = "Account password, when a code is not available", example = "correct-horse-battery-staple")
    String password
) {
}
