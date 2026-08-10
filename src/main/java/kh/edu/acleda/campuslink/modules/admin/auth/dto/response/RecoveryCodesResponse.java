package kh.edu.acleda.campuslink.modules.admin.auth.dto.response;

import java.util.List;

/**
 * A freshly generated set of recovery codes (ADM-026).
 *
 * <p>Shaped identically to {@link MfaConfirmResponse} but kept separate: both are published
 * contracts, and seeing "MfaConfirmResponse" on the regeneration endpoint in Swagger would
 * suggest it is part of enrolment, which it is not.
 *
 * <p>As at enrolment, this response is the only place the plaintext ever exists - the codes are
 * stored BCrypt-hashed. Unlike enrolment, issuing these <strong>invalidates the previous
 * set</strong>, so an admin who still had unspent codes on paper must discard them.
 *
 * @param recoveryCodes ten single-use codes, formatted {@code ABCDE-FGHJK}.
 */
public record RecoveryCodesResponse(
    List<String> recoveryCodes
) {
}
