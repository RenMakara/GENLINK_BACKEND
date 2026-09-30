package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;

/** One row in the author-only list of users who reacted to a discussion. */
public record DiscussionLikerResponse(
    UUID id,
    String fullName,
    String username,
    String avatarUrl,
    Instant likedAt
) {
}
