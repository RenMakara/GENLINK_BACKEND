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
import kh.edu.acleda.campuslink.modules.community.service.DiscussionService;
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

    /** Commenters are never anonymous - only the discussion author can be. */
    @GetMapping("/{discussionId}/comments")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> comments(
        @PathVariable UUID discussionId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Comments retrieved",
            discussionService.comments(discussionId, pageable)));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> deleteComment(@PathVariable UUID commentId) {
        discussionService.deleteComment(commentId);
        return ResponseEntity.ok(ApiResponse.success("Comment deleted", null));
    }
}
