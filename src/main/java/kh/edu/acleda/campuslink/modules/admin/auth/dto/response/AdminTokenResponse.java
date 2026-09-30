package kh.edu.acleda.campuslink.modules.admin.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** A real admin session, finally. */

public record AdminTokenResponse(
    @Schema(description = "typ=ADMIN token, accepted only on /api/v1/admin/**", example = "eyJhbGciOiJIUzUxMiJ9...")
    String accessToken,
    @Schema(description = "Rotated on every refresh; the previous one is revoked", example = "eyJhbGciOiJIUzUxMiJ9...")
    String refreshToken,
    @Schema(description = "Always the literal Bearer", example = "Bearer")
    String tokenType,
    @Schema(description = "Access token lifetime. 600 - shorter than mobile on purpose.", example = "600")
    long expiresInSeconds,
    @Schema(description = "Set ONLY when a recovery code was spent. Null for a normal TOTP sign-in.", example = "9")
    Long recoveryCodesRemaining
) {
}
