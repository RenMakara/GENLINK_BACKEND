package kh.edu.acleda.campuslink.modules.entertainment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.ChallengeResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.ChallengeService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/entertainment/challenges")
@Tag(name = "Entertainment - Challenges")
public class ChallengeController {

    private final ChallengeService challengeService;

    public ChallengeController(ChallengeService challengeService) {
        this.challengeService = challengeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<ChallengeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Challenge list retrieved", challengeService.getAll()));
    }
}
