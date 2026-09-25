package kh.edu.acleda.campuslink.modules.admin.audit.dto.request;

import java.time.LocalDate;

/** JSON body for the Super Admin audit-log export. */
public record AdminAuditLogExportRequest(
    String adminId,
    String action,
    String targetType,
    LocalDate from,
    LocalDate to,
    String status,
    String reason
) {

    public AdminAuditLogCriteria criteria() {
        return AdminAuditLogCriteria.fromRequest(
            adminId, action, targetType, from, to, status, 0, 100);
    }
}
