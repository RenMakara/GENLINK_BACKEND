package kh.edu.acleda.campuslink.modules.admin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The first code from the authenticator, proving enrolment actually worked.
 *
 * <p>Without this step an admin could be marked enrolled against a secret their app never
 * accepted - locking them out permanently at the next login, with no recovery codes issued yet.
 */
public record MfaConfirmRequest(

    @NotBlank(message = "Code is required")
    @Pattern(regexp = "\\d{6}", message = "Code must be 6 digits")
    @Schema(description = "First 6-digit code from the authenticator, proving enrolment works", example = "123456")
    String code
) {
}
