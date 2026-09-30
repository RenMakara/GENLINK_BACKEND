package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityPrivacy;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityRole;

/** @param myRole null when the caller is not an active member; drives Join vs Leave */
public record CommunityResponse(
    UUID communityId,
    String name,
    String slug,
    String description,
    String category,
    String coverImageUrl,
    String avatarUrl,
    CommunityPrivacy privacy,
    Integer memberCount,
    Integer postCount,
    boolean joined,
    CommunityRole myRole,
    boolean requestPending,
    Instant createdAt
) {
}
