package kh.edu.acleda.campuslink.modules.admin.rbac.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Audited reason for resetting another administrator's MFA enrolment. */
public record AdminMfaResetRequest(
    @NotBlank @Size(min = 10, max = 2000) String reason
) {
}
