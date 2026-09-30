package kh.edu.acleda.campuslink.modules.community.dto.response;

import kh.edu.acleda.campuslink.modules.community.enums.FollowStatus;

/** @param status ACCEPTED for an open profile, PENDING when the target's profile is */
public record FollowResponse(
    PostResponse.AuthorSummary user,
    FollowStatus status
) {
}
