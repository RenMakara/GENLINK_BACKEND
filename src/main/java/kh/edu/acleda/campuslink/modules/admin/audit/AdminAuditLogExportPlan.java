package kh.edu.acleda.campuslink.modules.admin.audit;

import kh.edu.acleda.campuslink.modules.admin.audit.dto.request.AdminAuditLogCriteria;
import kh.edu.acleda.campuslink.modules.admin.rbac.PermissionScopeResolver.AuditLogScope;

/** Immutable export state captured before a streaming response starts. */
record AdminAuditLogExportPlan(
    AdminAuditLogCriteria criteria,
    AuditLogScope scope,
    long rowCount,
    String reason
) {
}
