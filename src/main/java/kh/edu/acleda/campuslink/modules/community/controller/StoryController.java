package kh.edu.acleda.campuslink.modules.community.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.dto.request.StoryCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryFeedItem;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryResponse;
import kh.edu.acleda.campuslink.modules.community.dto.response.StoryViewerResponse;
import kh.edu.acleda.campuslink.modules.community.service.StoryService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/community/stories")
@Tag(name = "Community - Stories")
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    /** Post a story (media uploaded beforehand). Expires 24h from now. */
    @PostMapping
    public ResponseEntity<ApiResponse<StoryResponse>> create(@Valid @RequestBody StoryCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Story created", storyService.createStory(request)));
    }

    /** The story bar: your active stories plus those of people you follow, grouped by author. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<StoryFeedItem>>> feed() {
        return ResponseEntity.ok(ApiResponse.success("Stories retrieved", storyService.getStoriesFeed()));
    }

    /** Retrieve a single active story if authorized. */
    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<StoryResponse>> getStory(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Story retrieved", storyService.getStoryById(id)));
    }

    /** Mark a story seen. Idempotent; viewing your own story records nothing. */
    @PostMapping("/{id}/view")
    public ResponseEntity<ApiResponse<Void>> view(@PathVariable UUID id) {
        storyService.viewStory(id);
        return ResponseEntity.ok(ApiResponse.success("Story marked as seen", null));
    }

    /** Like a story. Emits a notification to the story author. */
    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse<Void>> like(@PathVariable UUID id) {
        storyService.likeStory(id);
        return ResponseEntity.ok(ApiResponse.success("Story liked", null));
    }

    /** Who viewed a story. Owner only; a non-owner gets 404, not 403. */
    @GetMapping("/{id}/viewers")
    public ResponseEntity<ApiResponse<List<StoryViewerResponse>>> viewers(@PathVariable UUID id) {
        return ResponseEntity.ok(ApiResponse.success("Story viewers retrieved", storyService.storyViewers(id)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        storyService.deleteStory(id);
        return ResponseEntity.ok(ApiResponse.success("Story deleted", null));
    }
}
