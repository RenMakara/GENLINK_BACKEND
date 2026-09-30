package kh.edu.acleda.campuslink.modules.security.dto.request;

import jakarta.validation.constraints.Size;

/** Confirmation payload for deleting the caller's own account. */
public record DeleteAccountRequest(
    @Size(max = 72) String password
) {
}
