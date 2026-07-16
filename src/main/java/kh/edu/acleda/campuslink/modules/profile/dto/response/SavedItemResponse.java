package kh.edu.acleda.campuslink.modules.profile.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;

/**
 * Returns the reference only. The client fetches the item's own detail endpoint
 * per type; an id that no longer resolves means the target was deleted.
 */
public record SavedItemResponse(
    UUID savedItemId,
    SavedItemType itemType,
    UUID itemId,
    LocalDateTime savedAt
) {
}
