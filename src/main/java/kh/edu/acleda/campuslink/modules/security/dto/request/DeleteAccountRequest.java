package kh.edu.acleda.campuslink.modules.security.dto.request;

import jakarta.validation.constraints.Size;

/**
 * Confirmation payload for deleting the caller's own account.
 *
 * <p>{@code password} is optional at the bean-validation level because social-only
 * accounts have no password to re-enter. The service still requires it whenever the
 * account actually has a password hash.
 */
public record DeleteAccountRequest(
    @Size(max = 72) String password
) {
}
