package kh.edu.acleda.campuslink.modules.community.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.CommentCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.CommentResponse;
import kh.edu.acleda.campuslink.modules.community.service.CommentService;
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
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community")
@Tag(name = "Community - Comments")
public class CommentController {

    private final CommentService commentService;

    public CommentController(CommentService commentService) {
        this.commentService = commentService;
    }

    @PostMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<CommentResponse>> add(@PathVariable UUID postId,
                                                            @Valid @RequestBody CommentCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Comment added", commentService.add(postId, request)));
    }

    /** Top-level comments. Replies come from the /replies route below. */
    @GetMapping("/posts/{postId}/comments")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> list(
        @PathVariable UUID postId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Comments retrieved", commentService.list(postId, pageable)));
    }

    @GetMapping("/comments/{commentId}/replies")
    public ResponseEntity<ApiResponse<PageResponse<CommentResponse>>> replies(
        @PathVariable UUID commentId,
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.ASC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Replies retrieved", commentService.replies(commentId, pageable)));
    }

    /** Idempotent - backs the comment like toggle. Returns the resulting count. */
    @PostMapping("/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> like(@PathVariable UUID commentId) {
        return ResponseEntity.ok(ApiResponse.success("Comment liked",
            Map.of("likeCount", commentService.like(commentId))));
    }

    @DeleteMapping("/comments/{commentId}/like")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> unlike(@PathVariable UUID commentId) {
        return ResponseEntity.ok(ApiResponse.success("Comment unliked",
            Map.of("likeCount", commentService.unlike(commentId))));
    }

    @DeleteMapping("/comments/{commentId}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID commentId) {
        commentService.delete(commentId);
        return ResponseEntity.ok(ApiResponse.success("Comment deleted", null));
    }
}
