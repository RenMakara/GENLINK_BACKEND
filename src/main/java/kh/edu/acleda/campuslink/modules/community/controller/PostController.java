package kh.edu.acleda.campuslink.modules.community.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.PollCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.PollVoteRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.PostCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.PostReportRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.RepostRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.PollResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostLikerResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ReportedPostResponse;
import kh.edu.acleda.campuslink.modules.community.service.PostService;
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
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community/posts")
@Tag(name = "Community - Posts")
public class PostController {

    private final PostService postService;

    public PostController(PostService postService) {
        this.postService = postService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<PostResponse>> create(@Valid @RequestBody PostCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Post created", postService.create(request)));
    }

    /** Create a poll: a POLL post carrying a question and 2-6 options. */
    @PostMapping("/poll")
    public ResponseEntity<ApiResponse<PostResponse>> createPoll(@Valid @RequestBody PollCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Poll created", postService.createPollPost(request)));
    }

    /** Cast or move your single vote on a poll. */
    @PostMapping("/{id}/poll/vote")
    public ResponseEntity<ApiResponse<PollResponse>> vote(@PathVariable UUID id,
                                                          @Valid @RequestBody PollVoteRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Vote recorded", postService.vote(id, request)));
    }

    /** The personalised feed: public posts, your own. */
    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> feed(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Feed retrieved", postService.getFeed(pageable)));
    }

    @GetMapping("/community/{communityId}")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> communityFeed(
        @PathVariable UUID communityId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Community feed retrieved",
            postService.getCommunityFeed(communityId, pageable)));
    }

    /** Reposts for the current user only; these are intentionally separate from the feed. */
    @GetMapping("/reposts")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> getReposts(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Reposts retrieved", postService.getReposts(pageable)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> getMine(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Post list retrieved", postService.getMine(pageable)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> getById(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Post retrieved", postService.getById(id)));
    }

    /** Repost a post for the current user. Repeating the request is idempotent. */
    @PostMapping("/{id}/repost")
    public ResponseEntity<ApiResponse<Object>> repost(
        @PathVariable UUID id,
        @Valid @RequestBody(required = false) RepostRequest request) {
        return ResponseEntity.ok(ApiResponse.<Object>success("Post reposted", postService.repost(id, request)));
    }

    /** Remove the current user's repost. Repeating the request is a no-op. */
    @DeleteMapping("/{id}/repost")
    public ResponseEntity<ApiResponse<Object>> unrepost(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.<Object>success("Post unreposted", postService.unrepost(id)));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<PostResponse>> update(@PathVariable UUID id,
                                                            @Valid @RequestBody PostCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Post updated", postService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        postService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Post deleted", null));
    }

    /** Remove a post as a community moderator (OWNER/ADMIN/MODERATOR of the post's community). */
    @DeleteMapping("/{id}/moderate")
    public ResponseEntity<ApiResponse<Void>> removeAsModerator(@PathVariable UUID id) {
        postService.removeAsModerator(id);
        return ResponseEntity.ok(ApiResponse.success("Post removed by moderator", null));
    }

    /** Flag a post for moderator review. Idempotent: one report per user. */
    @PostMapping("/{id}/report")
    public ResponseEntity<ApiResponse<Void>> report(@PathVariable UUID id,
                                                    @Valid @RequestBody PostReportRequest request) {
        postService.report(id, request);
        return ResponseEntity.ok(ApiResponse.success("Post reported", null));
    }

    /** A community moderator's queue: reported posts in the community, most-reported first. */
    @GetMapping("/community/{communityId}/reported")
    public ResponseEntity<ApiResponse<PageResponse<ReportedPostResponse>>> reportedPosts(
        @PathVariable UUID communityId,
        @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Reported posts retrieved",
            postService.reportedPosts(communityId, pageable)));
    }

    /** Clear a post's reports without removing it. OWNER/ADMIN/MODERATOR of its community. */
    @PostMapping("/{id}/dismiss-reports")
    public ResponseEntity<ApiResponse<Void>> dismissReports(@PathVariable UUID id) {
        postService.dismissReports(id);
        return ResponseEntity.ok(ApiResponse.success("Reports dismissed", null));
    }

    /** Idempotent - backs the like toggle. Returns the resulting count. */
    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> like(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Post liked", Map.of("likeCount", postService.like(id))));
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> unlike(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Post unliked", Map.of("likeCount", postService.unlike(id))));
    }

    @GetMapping("/{id}/likes")
    public ResponseEntity<ApiResponse<PageResponse<PostLikerResponse>>> getLikers(
        @PathVariable UUID id,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Post likers retrieved", postService.getLikers(id, pageable)));
    }

}
