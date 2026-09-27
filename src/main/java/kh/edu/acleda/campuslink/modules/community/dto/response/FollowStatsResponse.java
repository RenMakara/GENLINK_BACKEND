package kh.edu.acleda.campuslink.modules.community.dto.response;

import kh.edu.acleda.campuslink.modules.community.enums.FollowStatus;

/**
 * @param followStatus  the caller's relationship to this user: null when not
 *                      following, PENDING when awaiting approval
 * @param friend        mutual accepted follow, which is what FRIENDS visibility means
 * @param followsViewer whether this user follows the viewer
 */
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
