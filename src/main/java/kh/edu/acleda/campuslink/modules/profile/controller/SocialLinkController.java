package kh.edu.acleda.campuslink.modules.profile.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import kh.edu.acleda.campuslink.modules.profile.dto.request.SocialLinkUpsertRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.SocialLinkResponse;
import kh.edu.acleda.campuslink.modules.profile.enums.SocialPlatform;
import kh.edu.acleda.campuslink.modules.profile.service.SocialLinkService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/profile/social-links")
@Tag(name = "Profile - Social Links")
public class SocialLinkController {

    private final SocialLinkService socialLinkService;

    public SocialLinkController(SocialLinkService socialLinkService) {
        this.socialLinkService = socialLinkService;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<SocialLinkResponse>>> getMine() {
        return ResponseEntity.ok(ApiResponse.success("Social links retrieved", socialLinkService.getMine()));
    }

    /** One link per platform, so this creates or replaces rather than appends. */
    @PutMapping
    public ResponseEntity<ApiResponse<SocialLinkResponse>> upsert(@Valid @RequestBody SocialLinkUpsertRequest request) {
        return ResponseEntity.ok(ApiResponse.success("Social link saved", socialLinkService.upsert(request)));
    }

    @DeleteMapping("/{platform}")
    public ResponseEntity<ApiResponse<Void>> delete(@PathVariable SocialPlatform platform) {
        socialLinkService.delete(platform);
        return ResponseEntity.ok(ApiResponse.success("Social link removed", null));
    }
}
