package kh.edu.acleda.campuslink.modules.community.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.PostCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.PostResponse;
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

    /** The public feed. Rows arrive with author, likedByMe, savedByMe and hashtags. */
    @GetMapping("/feed")
    public ResponseEntity<ApiResponse<PageResponse<PostResponse>>> feed(
        @PageableDefault(size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Feed retrieved", postService.getFeed(pageable)));
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

    /** Idempotent - backs the like toggle. Returns the resulting count. */
    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> like(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Post liked", Map.of("likeCount", postService.like(id))));
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<ApiResponse<Map<String, Integer>>> unlike(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Post unliked", Map.of("likeCount", postService.unlike(id))));
    }
}
