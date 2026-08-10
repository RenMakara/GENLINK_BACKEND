package kh.edu.acleda.campuslink.modules.admin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The second factor: either a 6-digit TOTP code or one of the ten recovery codes.
 *
 * <p>One field rather than two. The server tells them apart by shape, and an admin locked out
 * of their phone should not have to find the right box on the form to say so.
 *
 * <p>No {@code @Pattern} here, deliberately. A format rule would reject a malformed recovery
 * code with VALIDATION_ERROR while a wrong one returns ADMIN_MFA_INVALID - a difference an
 * attacker can measure, and one that would let them map the code format without ever holding
 * a valid challenge. Shape is checked after the attempt is counted.
 */
public record MfaVerifyRequest(

    @NotBlank(message = "Code is required")
    @Schema(description = "A 6-digit TOTP code, or one of the ten recovery codes (ABCDE-FGHJK)", example = "123456")
    String code
) {
}
