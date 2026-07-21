package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.util.UUID;

/**
 * @param liked whether the current user liked this
 * @param saved whether the current user saved this
 */
public record ShortContentResponse(
    UUID shortContentId,
    String title,
    String description,
    String category,
    String videoUrl,
    String thumbnailUrl,
    UUID creatorId,
    int likeCount,
    int saveCount,
    int shareCount,
    boolean liked,
    boolean saved
) {
}
