package kh.edu.acleda.campuslink.modules.entertainment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.LeaderboardResponse;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardCategory;
import kh.edu.acleda.campuslink.modules.entertainment.enums.LeaderboardPeriod;
import kh.edu.acleda.campuslink.modules.entertainment.service.LeaderboardService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entertainment/leaderboards")
@Tag(name = "Entertainment - Leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    /** Top players + your rank. Defaults to all-time overall XP. */
    @GetMapping
    public ResponseEntity<ApiResponse<LeaderboardResponse>> get(
        @RequestParam(defaultValue = "OVERALL_XP") LeaderboardCategory category,
        @RequestParam(defaultValue = "ALL_TIME") LeaderboardPeriod period) {
        return ResponseEntity.ok(ApiResponse.success("Leaderboard retrieved",
            leaderboardService.getLeaderboard(category, period)));
    }
}
