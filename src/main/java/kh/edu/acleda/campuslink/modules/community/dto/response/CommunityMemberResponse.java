package kh.edu.acleda.campuslink.modules.community.dto.response;

import java.time.Instant;
import kh.edu.acleda.campuslink.modules.community.enums.CommunityRole;

public record CommunityMemberResponse(
    PostResponse.AuthorSummary user,
    CommunityRole role,
    Instant joinedAt
) {
}
