package kh.edu.acleda.campuslink.modules.admin.audit.dto.response;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;

/** Safe, compact row returned by the audit-log list endpoint. */
public record AdminAuditLogSummaryResponse(
    UUID auditLogId,
    Instant createdAt,
    UUID adminUserId,
    String adminName,
    String adminEmail,
    String action,
    String targetType,
    UUID targetId,
    AuditStatus status,
    String errorCode,
    String reason,
    String requestId,
    UUID batchId
) {
}
