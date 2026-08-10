package kh.edu.acleda.campuslink.modules.admin.auth.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * Credentials for the password step of admin login.
 *
 * <p>No {@code @Size} on the password. A length rule here would reject an over-long password
 * before it is ever compared, telling an attacker the shape of what is stored; it also has no
 * value on a login endpoint, where the password either matches what is on file or does not.
 * Strength rules belong at registration and password change, which is where they live.
 */
public record AdminLoginRequest(

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be valid")
    @Schema(description = "Registered admin email address", example = "admin@genlink.kh")
    String email,

    @NotBlank(message = "Password is required")
    @Schema(description = "Account password. Never logged, never echoed back.", example = "correct-horse-battery-staple")
    String password
) {
}
