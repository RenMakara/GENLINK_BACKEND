package kh.edu.acleda.campuslink.modules.admin.auth.dto.response;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * What the password step returns: a challenge, never a session.
 *
 * @param challengeToken 5-minute token that authenticates nothing. It is accepted only by
 *     {@code /admin/auth/mfa/verify}; every other route refuses it because its {@code typ}
 *     claim matches no route in the application.
 * @param expiresInSeconds so the portal can show a countdown and prompt a fresh login rather
 *     than letting the admin type six digits into a challenge that expired while they were
 *     looking for their phone.
 * @param enrolmentRequired true when this admin has never confirmed TOTP. The portal shows the
 *     enrolment screen instead of the code prompt. Safe to disclose: it is only ever returned
 *     once the password is correct <em>and</em> the account is a live admin, so it tells an
 *     attacker nothing they did not already have.
 */

public record AdminMfaChallengeResponse(
    @Schema(description = "5-minute token that authenticates nothing; accepted only by /mfa/verify", example = "eyJhbGciOiJIUzUxMiJ9...")
    String challengeToken,
    @Schema(description = "Challenge lifetime. Always 300.", example = "300")
    long expiresInSeconds,
    @Schema(description = "True when TOTP has never been confirmed - show the enrolment screen", example = "false")
    boolean enrolmentRequired
) {
}
