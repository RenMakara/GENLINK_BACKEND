package kh.edu.acleda.campuslink.modules.admin.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/** What the password step returns: a challenge, never a session. */

public record AdminMfaChallengeResponse(
    @Schema(description = "5-minute token that authenticates nothing; accepted only by /mfa/verify", example = "eyJhbGciOiJIUzUxMiJ9...")
    String challengeToken,
    @Schema(description = "Challenge lifetime. Always 300.", example = "300")
    long expiresInSeconds,
    @Schema(description = "True when TOTP has never been confirmed - show the enrolment screen", example = "false")
    boolean enrolmentRequired
) {
}
