package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardCategory;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardPeriod;

/** A leaderboard computed on read: the top players for a category/period plus the caller's own standing. */
public record LeaderboardResponse(
    LeaderboardCategory category,
    LeaderboardPeriod period,
    List<Entry> entries,
    Me me
) {

    public record Entry(int rank, UUID userId, String name, String avatarUrl, int level, long points) {}

    public record Me(Integer rank, long points) {}
}
