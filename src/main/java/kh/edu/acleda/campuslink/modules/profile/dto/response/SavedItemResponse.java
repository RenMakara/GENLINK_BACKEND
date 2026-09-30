package kh.edu.acleda.campuslink.modules.profile.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;

/** A saved row, ready to render without a follow-up call per item. */
public record SavedItemResponse(
    UUID savedItemId,
    SavedItemType itemType,
    UUID itemId,
    LocalDateTime savedAt,
    boolean available,
    String title,
    String subtitle,
    String imageUrl
) {
}
