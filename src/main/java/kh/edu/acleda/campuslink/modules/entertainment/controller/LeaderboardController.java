package kh.edu.acleda.campuslink.modules.entertainment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.LeaderboardResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.LeaderboardService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/entertainment/leaderboards")
@Tag(name = "Entertainment - Leaderboards")
public class LeaderboardController {

    private final LeaderboardService leaderboardService;

    public LeaderboardController(LeaderboardService leaderboardService) {
        this.leaderboardService = leaderboardService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<LeaderboardResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Leaderboard list retrieved", leaderboardService.getAll()));
    }
}
