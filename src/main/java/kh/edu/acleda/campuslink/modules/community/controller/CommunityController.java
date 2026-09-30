package kh.edu.acleda.campuslink.modules.community.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.ChangeMemberRoleRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.CommunityCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommunityMemberResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommunityResponse;
import kh.edu.acleda.campuslink.modules.community.service.CommunityService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community/communities")
@Tag(name = "Community - Communities")
public class CommunityController {

    private final CommunityService communityService;

    public CommunityController(CommunityService communityService) {
        this.communityService = communityService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<CommunityResponse>> create(@Valid @RequestBody CommunityCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Community created", communityService.create(request)));
    }

    /** Discovery. Public communities only; private ones are reached by direct link. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<CommunityResponse>>> discover(
        @RequestParam(required = false) String category,
        @PageableDefault(size = 20, sort = "memberCount", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Communities retrieved",
            communityService.discover(category, pageable)));
    }

    /** Literal path, declared before /{slug} so a community cannot shadow it. */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponse<CommunityResponse>>> getMine(
        @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("My communities retrieved", communityService.getMine(pageable)));
    }

    @GetMapping("/{slug}")
    public ResponseEntity<ApiResponse<CommunityResponse>> getBySlug(@PathVariable String slug) {
        return ResponseEntity.ok(ApiResponse.success("Community retrieved", communityService.getBySlug(slug)));
    }

    @PutMapping("/{communityId}")
    public ResponseEntity<ApiResponse<CommunityResponse>> update(@PathVariable UUID communityId,
                                                                 @Valid @RequestBody CommunityCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Community updated",
            communityService.update(communityId, request)));
    }

    @DeleteMapping("/{communityId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID communityId) {
        communityService.delete(communityId);
        return ResponseEntity.ok(ApiResponse.success("Community deleted", null));
    }

    /** Idempotent - backs the Join button. */
    @PostMapping("/{communityId}/join")
    public ResponseEntity<ApiResponse<CommunityResponse>> join(@PathVariable UUID communityId) {
        return ResponseEntity.ok(ApiResponse.success("Community joined", communityService.join(communityId)));
    }

    @DeleteMapping("/{communityId}/leave")
    public ResponseEntity<ApiResponse<Void>> leave(@PathVariable UUID communityId) {
        communityService.leave(communityId);
        return ResponseEntity.ok(ApiResponse.success("Community left", null));
    }

    @GetMapping("/{communityId}/members")
    public ResponseEntity<ApiResponse<PageResponse<CommunityMemberResponse>>> members(
        @PathVariable UUID communityId,
        @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Members retrieved",
            communityService.members(communityId, pageable)));
    }

    /** Pending join requests for a PRIVATE community. Owner/admin only; others get not-found. */
    @GetMapping("/{communityId}/join-requests")
    public ResponseEntity<ApiResponse<PageResponse<CommunityMemberResponse>>> joinRequests(
        @PathVariable UUID communityId,
        @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Join requests retrieved",
            communityService.joinRequests(communityId, pageable)));
    }

    @PostMapping("/{communityId}/join-requests/{userId}/approve")
    public ResponseEntity<ApiResponse<CommunityMemberResponse>> approveJoinRequest(
        @PathVariable UUID communityId, @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Join request approved",
            communityService.approveJoinRequest(communityId, userId)));
    }

    @DeleteMapping("/{communityId}/join-requests/{userId}")
    public ResponseEntity<ApiResponse<Void>> rejectJoinRequest(
        @PathVariable UUID communityId, @PathVariable UUID userId) {
        communityService.rejectJoinRequest(communityId, userId);
        return ResponseEntity.ok(ApiResponse.success("Join request rejected", null));
    }

    /** Promote/demote a member to ADMIN, MODERATOR or MEMBER. */
    @PutMapping("/{communityId}/members/{userId}/role")
    public ResponseEntity<ApiResponse<CommunityMemberResponse>> changeMemberRole(
        @PathVariable UUID communityId, @PathVariable UUID userId,
        @Valid @RequestBody ChangeMemberRoleRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Member role updated",
            communityService.changeMemberRole(communityId, userId, request.role())));
    }

    /** Transfer ownership to another active member. Current owner only; they become an ADMIN. */
    @PostMapping("/{communityId}/transfer-ownership/{userId}")
    public ResponseEntity<ApiResponse<CommunityResponse>> transferOwnership(
        @PathVariable UUID communityId, @PathVariable UUID userId) {
        return ResponseEntity.ok(ApiResponse.success("Ownership transferred",
            communityService.transferOwnership(communityId, userId)));
    }
}
