package kh.edu.acleda.campuslink.modules.community.dto.response;

import kh.edu.acleda.campuslink.modules.community.enums.FollowStatus;

/** @param followStatus the caller's relationship to this user: null when not */
public record FollowStatsResponse(
    long followers,
    long following,
    FollowStatus followStatus,
    boolean friend,
    boolean followsViewer
) {
    public FollowStatsResponse(long followers, long following, FollowStatus followStatus, boolean friend) {
        this(followers, following, followStatus, friend, false);
    }
}
