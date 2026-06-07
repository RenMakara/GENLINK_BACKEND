package kh.edu.acleda.campuslink.modules.entertainment.service;

import kh.edu.acleda.campuslink.modules.entertainment.dto.response.LeaderboardResponse;

import java.util.List;

public interface LeaderboardService {
    List<LeaderboardResponse> getAll();
}
