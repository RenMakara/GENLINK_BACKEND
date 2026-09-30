package kh.edu.acleda.campuslink.modules.admin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

/** The refresh token being exchanged. */
public record AdminRefreshRequest(

    @NotBlank(message = "Refresh token is required")
    @Schema(description = "The token being exchanged. Rotated: it is revoked as it is spent.", example = "eyJhbGciOiJIUzUxMiJ9...")
    String refreshToken
) {
}
