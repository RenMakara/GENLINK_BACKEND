package kh.edu.acleda.campuslink.modules.admin.sanction.dto.response;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionSeverity;
import kh.edu.acleda.campuslink.modules.admin.sanction.enums.SanctionType;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;

/** Safe staff projection of one warning, suspension or ban history row. */
public record AdminSanctionResponse(
    UUID sanctionId,
    UUID userId,
    String userFullName,
    SanctionType type,
    SanctionSeverity severity,
    String reason,
    String messageEn,
    String messageKm,
    AccountStatus previousStatus,
    Instant startsAt,
    Instant expiresAt,
    Instant acknowledgedAt,
    Instant liftedAt,
    String liftReason,
    UUID issuedByAdminId,
    String issuedByAdminName,
    UUID liftedByAdminId,
    String liftedByAdminName,
    boolean active
) {
}
