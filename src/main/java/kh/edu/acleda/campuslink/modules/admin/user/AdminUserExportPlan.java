package kh.edu.acleda.campuslink.modules.admin.user;

import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserListCriteria;

/** Validated, permission-aware snapshot used by the synchronous user CSV export. */
public record AdminUserExportPlan(
    AdminUserListCriteria criteria,
    long rowCount,
    boolean piiIncluded
) {
}
