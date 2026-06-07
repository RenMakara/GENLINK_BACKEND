package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.PostType;
import kh.edu.acleda.campuslink.modules.community.enums.Visibility;
import kh.edu.acleda.campuslink.shared.enums.Language;

public record PostResponse(
    UUID postId,
    String contentText,
    String imageUrl,
    PostType postType,
    Visibility visibility,
    Language language,
    Integer likeCount,
    Integer commentCount
) {
}
