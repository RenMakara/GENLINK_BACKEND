package kh.edu.acleda.campuslink.modules.entertainment.service.impl;

import kh.edu.acleda.campuslink.modules.entertainment.dto.response.LeaderboardResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.LeaderboardService;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class LeaderboardServiceImpl implements LeaderboardService {

    @Override
    public List<LeaderboardResponse> getAll() {
        // TODO: Load Leaderboard data from repository with localized fields selected by LanguageResolver.
        return List.of();
    }
}
