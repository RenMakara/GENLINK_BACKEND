package kh.edu.acleda.campuslink.modules.admin.audit;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import lombok.Builder;

/** One privileged action, as the caller describes it. */
@Builder
public record AdminAuditEvent(
    String action,
    String targetType,
    UUID targetId,
    String beforeJson,
    String afterJson,
    String reason,
    UUID batchId,
    AuditStatus status,
    String errorCode
) {

    public boolean isFailure() {
        return status == AuditStatus.FAILURE;
    }
}
