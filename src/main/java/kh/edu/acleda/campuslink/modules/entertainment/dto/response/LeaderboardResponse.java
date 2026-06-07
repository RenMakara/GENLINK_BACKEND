package kh.edu.acleda.campuslink.modules.entertainment.dto.response;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardCategory;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardPeriod;

public record LeaderboardResponse(
    UUID leaderboardId,
    UUID userId,
    LeaderboardCategory category,
    LeaderboardPeriod period,
    Integer rank,
    Integer points
) {
}
