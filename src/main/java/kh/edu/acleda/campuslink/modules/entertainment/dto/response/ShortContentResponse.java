package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.util.UUID;

public record ShortContentResponse(
    UUID shortContentId,
    String title,
    String description,
    String category,
    String videoUrl,
    String thumbnailUrl,
    UUID creatorId,
    String creatorFullName,
    String creatorUsername,
    String creatorAvatarUrl,
    int likeCount,
    int saveCount,
    int shareCount,
    boolean liked,
    boolean saved
) {
}
