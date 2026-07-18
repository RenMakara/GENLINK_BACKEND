package kh.edu.acleda.campuslink.modules.productivity.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.productivity.dto.response.MotivationResponse;
import kh.edu.acleda.campuslink.modules.productivity.service.MotivationService;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.localization.LanguageResolver;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/productivity/motivation")
@Tag(name = "Productivity - Motivation")
public class MotivationController {

    private final MotivationService motivationService;
    private final LanguageResolver languageResolver;

    public MotivationController(MotivationService motivationService, LanguageResolver languageResolver) {
        this.motivationService = motivationService;
        this.languageResolver = languageResolver;
    }

    @GetMapping
    public ResponseEntity<ApiResponse<List<MotivationResponse>>> getAll(
        @RequestParam(required = false) String category,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Motivation retrieved",
            motivationService.getAll(category, language(userDetails, acceptLanguage))));
    }

    /** Literal path, declared before /{id} so it cannot be shadowed. */
    @GetMapping("/saved")
    public ResponseEntity<ApiResponse<List<MotivationResponse>>> getSaved(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Saved motivation retrieved",
            motivationService.getSaved(language(userDetails, acceptLanguage))));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ApiResponse<MotivationResponse>> getById(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Motivation retrieved",
            motivationService.getById(id, language(userDetails, acceptLanguage))));
    }

    @PostMapping("/{id}/save")
    public ResponseEntity<ApiResponse<MotivationResponse>> save(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Motivation saved",
            motivationService.save(id, language(userDetails, acceptLanguage))));
    }

    @DeleteMapping("/{id}/save")
    public ResponseEntity<ApiResponse<MotivationResponse>> unsave(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Motivation unsaved",
            motivationService.unsave(id, language(userDetails, acceptLanguage))));
    }

    private Language language(UserDetails userDetails, String acceptLanguage) {
        String email = userDetails == null ? null : userDetails.getUsername();
        return languageResolver.resolve(email, acceptLanguage);
    }
}
