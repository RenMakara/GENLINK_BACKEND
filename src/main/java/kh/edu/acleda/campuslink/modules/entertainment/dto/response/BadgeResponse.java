package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.util.UUID;

public record BadgeResponse(
    UUID badgeId,
    String nameEn,
    String nameKm,
    String descriptionEn,
    String descriptionKm,
    String iconUrl
) {
}
