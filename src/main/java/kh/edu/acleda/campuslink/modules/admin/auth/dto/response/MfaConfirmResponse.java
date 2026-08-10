package kh.edu.acleda.campuslink.modules.admin.auth.dto.response;

import java.util.List;

/**
 * Recovery codes, shown exactly once (SSOT §10.8).
 *
 * <p>They are stored as BCrypt hashes, so this response is the only moment the plaintext
 * exists anywhere. If the admin closes the page without saving them, the codes are gone and a
 * fresh set has to be generated - which is the intended property, not a shortcoming.
 *
 * @param recoveryCodes ten single-use codes, formatted {@code ABCDE-FGHJK}.
 */
public record MfaConfirmResponse(
    List<String> recoveryCodes
) {
}
