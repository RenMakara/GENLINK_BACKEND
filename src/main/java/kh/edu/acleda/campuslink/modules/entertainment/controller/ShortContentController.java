package kh.edu.acleda.campuslink.modules.entertainment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.request.ShortContentCreateRequest;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.ShortContentResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.ShortContentService;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.localization.LanguageResolver;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entertainment/short-content")
@Tag(name = "Entertainment - Short Content")
public class ShortContentController {

    private final ShortContentService shortContentService;
    private final LanguageResolver languageResolver;

    public ShortContentController(ShortContentService shortContentService, LanguageResolver languageResolver) {
        this.shortContentService = shortContentService;
        this.languageResolver = languageResolver;
    }

    @PostMapping
    public ResponseEntity<ApiResponse<ShortContentResponse>> create(
        @Valid @RequestBody ShortContentCreateRequest request,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Short content created",
            shortContentService.create(request, language(userDetails, acceptLanguage))));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<ApiResponse<Void>> delete(
        @PathVariable UUID id) {
        shortContentService.delete(id);
        return ResponseEntity.ok(ApiResponse.success("Short content deleted", null));
    }

    /** The feed, newest first; optional ?category. */
    @GetMapping
    public ResponseEntity<ApiResponse<PageResponse<ShortContentResponse>>> feed(
        @RequestParam(required = false) String category,
        @PageableDefault(size = 20) Pageable pageable,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Short content retrieved",
            shortContentService.getFeed(category, pageable, language(userDetails, acceptLanguage))));
    }

    /** Literal path, declared before /{id} so it cannot be shadowed. */
    @GetMapping("/saved")
    public ResponseEntity<ApiResponse<List<ShortContentResponse>>> saved(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Saved short content retrieved",
            shortContentService.getSaved(language(userDetails, acceptLanguage))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<ShortContentResponse>> getById(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Short content retrieved",
            shortContentService.getById(id, language(userDetails, acceptLanguage))));
    }

    @PostMapping("/{id}/like")
    public ResponseEntity<ApiResponse<ShortContentResponse>> like(
        @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Liked",
            shortContentService.like(id, language(userDetails, acceptLanguage))));
    }

    @DeleteMapping("/{id}/like")
    public ResponseEntity<ApiResponse<ShortContentResponse>> unlike(
        @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Unliked",
            shortContentService.unlike(id, language(userDetails, acceptLanguage))));
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<ApiResponse<ShortContentResponse>> save(
        @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Saved",
            shortContentService.save(id, language(userDetails, acceptLanguage))));
    }

    @DeleteMapping("/{id}/save")
    public ResponseEntity<ApiResponse<ShortContentResponse>> unsave(
        @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Unsaved",
            shortContentService.unsave(id, language(userDetails, acceptLanguage))));
    }

    @PostMapping("/{id}/share")
    public ResponseEntity<ApiResponse<ShortContentResponse>> share(
        @PathVariable UUID id, @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Shared",
            shortContentService.share(id, language(userDetails, acceptLanguage))));
    }

    private Language language(UserDetails userDetails, String acceptLanguage) {
        String email = userDetails == null ? null : userDetails.getUsername();
        return languageResolver.resolve(email, acceptLanguage);
    }
}
