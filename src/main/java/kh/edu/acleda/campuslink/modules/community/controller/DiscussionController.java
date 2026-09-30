package kh.edu.acleda.campuslink.modules.community.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.CommentCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionReportRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommentResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionLikerResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.ReportedDiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.service.DiscussionService;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping("/api/v1/community/discussions")
@Tag(name = "Community - Discussions")
public class DiscussionController {

    private final DiscussionService discussionService;

    public DiscussionController(DiscussionService discussionService) {
        this.discussionService = discussionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DiscussionResponse>> create(
        @Valid @RequestBody DiscussionCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Discussion created", discussionService.create(request)));
    }

    /** The forum list. Anonymous discussions come back with author = null. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<DiscussionResponse>>> browse(
        @RequestParam(required = false) String category,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Discussions retrieved",
            discussionService.browse(category, pageable)));
    }

    /** Literal path, declared before /{discussionId} so it cannot be shadowed. */
    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<PageResponse<DiscussionResponse>>> getMine(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("My discussions retrieved", discussionService.getMine(pageable)));
    }

    /**
     * Admin moderation queue: reported discussions, most-reported first. ROLE_ADMIN only -
     * the forum is category-scoped, not community-scoped, so there is no community moderator
     * to defer to. Also a literal path declared before /{discussionId} so it is not shadowed.
     * The queue reveals the author even for anonymous discussions (moderation accountability).
     */
    @GetMapping("/reported")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<ReportedDiscussionResponse>>> reportedDiscussions(
        @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Reported discussions retrieved",
            discussionService.reportedDiscussions(pageable)));
    }

    /**
     * Profile-only repost history. Reposts are deliberately not merged into the forum
     * browse response; this endpoint is what makes them visible on the reposter's profile.
     */
    @GetMapping("/reposts")
    public ResponseEntity<ApiResponse<PageResponse<DiscussionResponse>>> getReposts(
        @RequestParam(required = false) UUID userId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Discussion reposts retrieved",
            discussionService.getReposts(userId, pageable)));
    }

    @GetMapping("/{discussionId}")
    public ResponseEntity<ApiResponse<DiscussionResponse>> getById(@PathVariable UUID discussionId) {
        return ResponseEntity.ok(ApiResponse.success("Discussion retrieved", discussionService.getById(discussionId)));
    }

    @PutMapping("/{discussionId}")
    public ResponseEntity<ApiResponse<DiscussionResponse>> update(
        @PathVariable UUID discussionId, @Valid @RequestBody DiscussionCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Discussion updated",
            discussionService.update(discussionId, request)));
    }

    @DeleteMapping("/{discussionId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID discussionId) {
        discussionService.delete(discussionId);
        return ResponseEntity.ok(ApiResponse.success("Discussion deleted", null));
    }

    /** Idempotent - backs the react toggle. Returns the resulting count. */
    @PostMapping("/{discussionId}/react")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> react(@PathVariable UUID discussionId) {
        return ResponseEntity.ok(ApiResponse.success("Reacted",
            Map.of("reactionCount", discussionService.react(discussionId))));
    }

    @DeleteMapping("/{discussionId}/react")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> unreact(@PathVariable UUID discussionId) {
        return ResponseEntity.ok(ApiResponse.success("Reaction removed",
            Map.of("reactionCount", discussionService.unreact(discussionId))));
    }

    /** The discussion author can inspect a paged list of users who reacted. */
    @GetMapping("/{discussionId}/reactions")
    public ResponseEntity<ApiResponse<PageResponse<DiscussionLikerResponse>>> getReactions(
        @PathVariable UUID discussionId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC)
        Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Discussion reactions retrieved",
            discussionService.getLikers(discussionId, pageable)));
    }

    /** Discussions are forum topics and cannot be reposted as feed posts. */
    @PostMapping("/{discussionId}/repost")
    public ResponseEntity<ApiResponse<Void>> repost(
        @PathVariable UUID discussionId) {
        throw new ApiException("Discussions cannot be reposted", "DISCUSSION_REPOST_NOT_SUPPORTED");
    }

    /** Discussion repost records remain readable as legacy history but cannot be changed. */
    @DeleteMapping("/{discussionId}/repost")
    public ResponseEntity<ApiResponse<Void>> unrepost(@PathVariable UUID discussionId) {
        throw new ApiException("Discussions cannot be reposted", "DISCUSSION_REPOST_NOT_SUPPORTED");
    }

    /** One report per user. Reporting twice is a silent no-op, not an error. */
    @PostMapping("/{discussionId}/report")
    public ResponseEntity<ApiResponse<Void>> report(@PathVariable UUID discussionId,
                                                    @Valid @RequestBody DiscussionReportRequest request) {
        discussionService.report(discussionId, request);
        return ResponseEntity.ok(ApiResponse.success("Discussion reported", null));
    }

    @PostMapping("/{discussionId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> addComment(
        @PathVariable UUID discussionId, @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Comment added",
            discussionService.addComment(discussionId, request)));
    }

    @PutMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<CommentResponse>> updateComment(
        @PathVariable UUID commentId, @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Comment updated",
            discussionService.updateComment(commentId, request.content())));
    }

    /** Top-level comments only. Commenters are never anonymous - only the discussion author can be. */
    @GetMapping("/{discussionId}/comments")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> comments(
        @PathVariable UUID discussionId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Comments retrieved",
            discussionService.comments(discussionId, pageable)));
    }

    /** Replies to one comment. Threading is one level deep - a reply to a reply attaches here too. */
    @GetMapping("/comments/{commentId}/replies")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> replies(
        @PathVariable UUID commentId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Replies retrieved",
            discussionService.replies(commentId, pageable)));
    }

    /** Idempotent - backs the comment like toggle. Returns the resulting count. */
    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> likeComment(@PathVariable UUID commentId) {
        return ResponseEntity.ok(ApiResponse.success("Comment liked",
            Map.of("likeCount", discussionService.likeComment(commentId))));
    }

    @DeleteMapping("/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> unlikeComment(@PathVariable UUID commentId) {
        return ResponseEntity.ok(ApiResponse.success("Comment unliked",
            Map.of("likeCount", discussionService.unlikeComment(commentId))));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable UUID commentId) {
        discussionService.deleteComment(commentId);
        return ResponseEntity.ok(ApiResponse.success("Comment deleted", null));
    }

    /** Remove any reported discussion as an admin (soft delete). ROLE_ADMIN only. */
    @DeleteMapping("/{discussionId}/moderate")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> removeAsAdmin(@PathVariable UUID discussionId) {
        discussionService.removeAsAdmin(discussionId);
        return ResponseEntity.ok(ApiResponse.success("Discussion removed", null));
    }

    /** Reviewed and fine: clear the discussion's reports without removing it. ROLE_ADMIN only. */
    @PostMapping("/{discussionId}/dismiss-reports")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Void>> dismissReports(@PathVariable UUID discussionId) {
        discussionService.dismissReports(discussionId);
        return ResponseEntity.ok(ApiResponse.success("Reports dismissed", null));
    }
}
