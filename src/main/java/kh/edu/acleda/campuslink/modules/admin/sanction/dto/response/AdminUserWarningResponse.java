package kh.edu.acleda.campuslink.modules.admin.sanction.dto.response;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionSeverity;

/** Warning result that makes the Phase 8 delivery gap explicit to the Admin UI. */
public record AdminUserWarningResponse(
    UUID warningId,
    UUID userId,
    SanctionSeverity severity,
    String messageEn,
    String messageKm,
    boolean delivered,
    String deliveryStatus,
    Instant recordedAt
) {
}
