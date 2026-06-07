package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.util.UUID;
import kh.edu.acleda.campuslink.shared.enums.Language;

public record DiscussionResponse(
    UUID discussionId,
    String title,
    String content,
    String category,
    Boolean anonymous,
    Language language,
    Integer reactionCount,
    Integer commentCount
) {
}
