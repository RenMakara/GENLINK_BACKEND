package kh.edu.acleda.campuslink.modules.admin.badge.dto.response;

import java.util.UUID;

/** Non-sensitive badge metadata used by the Admin bulk assignment picker. */
public record AdminBadgeResponse(
    UUID badgeId,
    String nameEn,
    String nameKm,
    String descriptionEn,
    String descriptionKm,
    String requirement,
    String iconUrl
) {
}
