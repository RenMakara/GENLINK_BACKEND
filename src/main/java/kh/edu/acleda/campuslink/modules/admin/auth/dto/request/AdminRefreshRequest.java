package kh.edu.acleda.campuslink.modules.admin.auth.dto.request;

import jakarta.validation.constraints.NotBlank;
import io.swagger.v3.oas.annotations.media.Schema;

/**
 * The refresh token being exchanged.
 *
 * <p>In the request body rather than a header: it is a credential the portal holds in an
 * httpOnly cookie (SSOT §10.9), and putting it in a URL or a custom header makes it far easier
 * to leak into an access log or a proxy trace.
 */
public record AdminRefreshRequest(

    @NotBlank(message = "Refresh token is required")
    @Schema(description = "The token being exchanged. Rotated: it is revoked as it is spent.", example = "eyJhbGciOiJIUzUxMiJ9...")
    String refreshToken
) {
}
