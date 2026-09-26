package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;

/** One row of a post's liker list. */
public record PostLikerResponse(
    UUID id,
    UUID userId,
    String fullName,
    String username,
    String avatarUrl,
    Instant likedAt
) {
}
