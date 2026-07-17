package kh.edu.acleda.campuslink.modules.community.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.response.FollowResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.FollowStatsResponse;
import kh.edu.acleda.campuslink.modules.community.service.FollowService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community")
@Tag(name = "Community - Follows")
public class FollowController {

    private final FollowService followService;

    public FollowController(FollowService followService) {
        this.followService = followService;
    }

    /** Idempotent. PENDING when the target's profile is PRIVATE, else ACCEPTED. */
    @PostMapping("/users/{userId}/follow")
    public ResponseEntity<ApiResponse<FollowResponse>> follow(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Followed", followService.follow(userId)));
    }

    @DeleteMapping("/users/{userId}/follow")
    public ResponseEntity<ApiResponse<Void>> unfollow(@PathVariable UUID userId) {
        followService.unfollow(userId);
        return ResponseEntity.ok(ApiResponse.success("Unfollowed", null));
    }

    @GetMapping("/users/{userId}/followers")
    public ResponseEntity<ApiResponse<PageResponse<FollowResponse>>> followers(
        @PathVariable UUID userId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Followers retrieved",
            followService.followers(userId, pageable)));
    }

    @GetMapping("/users/{userId}/following")
    public ResponseEntity<ApiResponse<PageResponse<FollowResponse>>> following(
        @PathVariable UUID userId, @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Following retrieved",
            followService.following(userId, pageable)));
    }

    /** followStatus and friend let the client render Follow / Requested / Following. */
    @GetMapping("/users/{userId}/follow-stats")
    public ResponseEntity<ApiResponse<FollowStatsResponse>> stats(@PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Follow stats retrieved", followService.stats(userId)));
    }

    @GetMapping("/follow-requests")
    public ResponseEntity<ApiResponse<PageResponse<FollowResponse>>> pendingRequests(
        @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Follow requests retrieved",
            followService.pendingRequests(pageable)));
    }

    @PostMapping("/follow-requests/{followerUserId}/accept")
    public ResponseEntity<ApiResponse<FollowResponse>> accept(@PathVariable UUID followerUserId) {
        return ResponseEntity.ok(ApiResponse.success("Follow request accepted",
            followService.acceptRequest(followerUserId)));
    }

    @DeleteMapping("/follow-requests/{followerUserId}")
    public ResponseEntity<ApiResponse<Void>> reject(@PathVariable UUID followerUserId) {
        followService.rejectRequest(followerUserId);
        return ResponseEntity.ok(ApiResponse.success("Follow request rejected", null));
    }
}
