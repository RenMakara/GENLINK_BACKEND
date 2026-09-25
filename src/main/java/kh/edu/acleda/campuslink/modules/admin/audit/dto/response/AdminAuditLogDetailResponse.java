package kh.edu.acleda.campuslink.modules.admin.audit.dto.response;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;

/** Full audit record, with snapshots exposed as JSON trees for diff-capable clients. */
public record AdminAuditLogDetailResponse(
    UUID auditLogId,
    Instant createdAt,
    UUID adminUserId,
    String adminName,
    String adminEmail,
    String action,
    String targetType,
    UUID targetId,
    JsonNode beforeJson,
    JsonNode afterJson,
    String reason,
    String ipAddress,
    String userAgent,
    String requestId,
    UUID batchId,
    AuditStatus status,
    String errorCode
) {
}
