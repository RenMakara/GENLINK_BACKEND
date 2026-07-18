package kh.edu.acleda.campuslink.modules.community.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kh.edu.acleda.campuslink.modules.community.service.HashtagService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community/hashtags")
@Tag(name = "Community - Hashtags")
public class HashtagController {

    private final HashtagService hashtagService;

    public HashtagController(HashtagService hashtagService) {
        this.hashtagService = hashtagService;
    }

    /** Backs "Trending" in the Social Feed flow. Ranked by usage count. */
    @GetMapping("/trending")
    public ResponseEntity<ApiResponse<List<String>>> trending(
        @RequestParam(defaultValue = "10") int limit) {
        int capped = Math.min(Math.max(limit, 1), 50);
        return ResponseEntity.ok(ApiResponse.success("Trending hashtags retrieved", hashtagService.trending(capped)));
    }
}
