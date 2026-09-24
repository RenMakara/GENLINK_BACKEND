package kh.edu.acleda.campuslink.modules.admin.user.dto.response;

import java.util.UUID;

/** Result of a signed admin XP adjustment, including the actual delta after flooring. */
public record AdminUserXpAdjustmentResponse(
    UUID userId,
    int requestedAmount,
    int appliedAmount,
    int totalXp,
    int level
) {
}
