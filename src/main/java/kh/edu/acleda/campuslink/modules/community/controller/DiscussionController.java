package kh.edu.acleda.campuslink.modules.community.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.community.dto.request.DiscussionCreateRequest;
import kh.edu.acleda.campuslink.modules.community.dto.response.DiscussionResponse;
import kh.edu.acleda.campuslink.modules.community.service.DiscussionService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/community/discussions")
@Tag(name = "Community - Discussions")
public class DiscussionController {

    private final DiscussionService discussionService;

    public DiscussionController(DiscussionService discussionService) {
        this.discussionService = discussionService;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<DiscussionResponse>> create(@Valid @RequestBody DiscussionCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Discussion created", discussionService.create(request)));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<DiscussionResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success("Discussion list retrieved", discussionService.getMine()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ApiResponse<DiscussionResponse>> update(@PathVariable UUID id, @Valid @RequestBody DiscussionCreateRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Discussion updated", discussionService.update(id, request)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable UUID id) {
        discussionService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Discussion deleted", null));
    }
}
