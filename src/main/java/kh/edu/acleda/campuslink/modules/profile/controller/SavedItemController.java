package kh.edu.acleda.campuslink.modules.profile.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.profile.dto.request.SaveItemRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.SavedItemResponse;
import kh.edu.acleda.campuslink.modules.profile.enums.SavedItemType;
import kh.edu.acleda.campuslink.modules.profile.service.SavedItemService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * One saved list across every domain, backed by saved_items. saved_posts and
 * user_saved_motivations are superseded by this.
 */
@RestController
@RequestMapping("/api/v1/profile/saved")
@Tag(name = "Profile - Saved Items")
public class SavedItemController {

    private final SavedItemService savedItemService;

    public SavedItemController(SavedItemService savedItemService) {
        this.savedItemService = savedItemService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SavedItemResponse>>> getMine(
        @RequestParam(required = false) SavedItemType type) {
        return ResponseEntity.ok(ApiResponse.success("Saved items retrieved", savedItemService.getMine(type)));
    }

    @PostMapping
    public ResponseEntity<ApiResponse<SavedItemResponse>> save(@Valid @RequestBody SaveItemRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Item saved", savedItemService.save(request)));
    }

    @DeleteMapping("/{itemType}/{itemId}")
    public ResponseEntity<ApiResponse<Void>> unsave(@PathVariable SavedItemType itemType,
                                                    @PathVariable UUID itemId) {
        savedItemService.unsave(itemType, itemId);
        return ResponseEntity.ok(ApiResponse.success("Item unsaved", null));
    }

    /** Lets the client render the save toggle without pulling the whole list. */
    @GetMapping("/check")
    public ResponseEntity<ApiResponse<Map<String, Boolean>>> isSaved(@RequestParam SavedItemType itemType,
                                                                     @RequestParam UUID itemId) {
        boolean saved = savedItemService.isSaved(itemType, itemId);
        return ResponseEntity.ok(ApiResponse.success("Checked", Map.of("saved", saved)));
    }
}
