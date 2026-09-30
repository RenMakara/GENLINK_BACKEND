package kh.edu.acleda.campuslink.modules.admin.auth.dto.response;

import java.util.List;

/** Recovery codes, shown exactly once (SSOT §10.8). */
public record MfaConfirmResponse(
    List<String> recoveryCodes
) {
}
