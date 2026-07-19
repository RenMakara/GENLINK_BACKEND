package kh.edu.acleda.campuslink.modules.entertainment.service;

import kh.edu.acleda.campuslink.modules.entertainment.dto.response.LeaderboardResponse;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardCategory;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardPeriod;

public interface LeaderboardService {

    /** Top players for a category/period, plus the caller's own rank. Computed on read. */
    LeaderboardResponse getLeaderboard(LeaderboardCategory category, LeaderboardPeriod period);
}
