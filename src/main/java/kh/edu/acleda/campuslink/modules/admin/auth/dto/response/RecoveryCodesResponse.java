package kh.edu.acleda.campuslink.modules.admin.auth.dto.response;

import java.util.List;

/** A freshly generated set of recovery codes (ADM-026). */
public record RecoveryCodesResponse(
    List<String> recoveryCodes
) {
}
