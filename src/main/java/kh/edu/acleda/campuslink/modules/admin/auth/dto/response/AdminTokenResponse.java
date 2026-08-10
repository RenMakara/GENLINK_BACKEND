package kh.edu.acleda.campuslink.modules.admin.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * A real admin session, finally.
 *
 * @param accessToken {@code typ=ADMIN}, 10 minutes. Accepted only on {@code /api/v1/admin/**}.
 * @param refreshToken opaque to the client; stored server-side as a SHA-256 hash. 8 hours -
 *     about a working day, against the mobile app's 7 days.
 * @param tokenType always {@code Bearer}. Present so the portal can build the header without
 *     hard-coding the scheme.
 * @param expiresInSeconds lifetime of the access token, so the client can refresh before it
 *     lapses rather than discovering it through a failed request mid-action.
 * @param recoveryCodesRemaining how many recovery codes are left, or null when the admin signed
 *     in with TOTP. Surfaced so someone who has just spent their second-to-last code is told
 *     while they are still able to generate more - after the last one there is no self-service
 *     way back.
 */

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
