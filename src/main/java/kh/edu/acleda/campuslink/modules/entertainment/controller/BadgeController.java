package kh.edu.acleda.campuslink.modules.entertainment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.BadgeResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.BadgeService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/entertainment/badges")
@Tag(name = "Entertainment - Badges")
public class BadgeController {

    private final BadgeService badgeService;

    public BadgeController(BadgeService badgeService) {
        this.badgeService = badgeService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<BadgeResponse>>> getAll() {
        return ResponseEntity.ok(ApiResponse.success("Badge list retrieved", badgeService.getAll()));
    }
}
