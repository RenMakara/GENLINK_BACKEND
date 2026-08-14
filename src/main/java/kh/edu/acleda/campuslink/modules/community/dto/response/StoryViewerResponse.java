package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;

/** One row of a story's viewer list, shown only to the story's owner. */
public record StoryViewerResponse(
    UUID userId,
    String fullName,
    String username,
    String avatarUrl,
    Instant viewedAt
) {
}
