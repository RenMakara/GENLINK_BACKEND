package kh.edu.acleda.campuslink.modules.profile.dto.response;

import java.time.LocalDateTime;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;

/**
 * A saved row, ready to render without a follow-up call per item.
 *
 * @param available false when the saved thing has since been deleted. item_id is not
 *                  a foreign key (saved_items is polymorphic), so this can happen;
 *                  title, subtitle and imageUrl are null in that case and the client
 *                  should show a "no longer available" row rather than a blank one.
 */
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
