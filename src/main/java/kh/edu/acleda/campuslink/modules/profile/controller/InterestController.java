package kh.edu.acleda.campuslink.modules.profile.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import kh.edu.acleda.campuslink.modules.profile.dto.request.UserInterestsUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.response.InterestResponse;
import kh.edu.acleda.campuslink.modules.profile.service.InterestService;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.localization.LanguageResolver;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1")
@Tag(name = "Profile - Interests")
public class InterestController {

    private final InterestService interestService;
    private final LanguageResolver languageResolver;

    public InterestController(InterestService interestService, LanguageResolver languageResolver) {
        this.interestService = interestService;
        this.languageResolver = languageResolver;
    }

    /** Catalogue for the onboarding chooser. */
    @GetMapping("/interests")
    public ResponseEntity<ApiResponse<List<InterestResponse>>> catalog(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        Language language = resolve(userDetails, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success("Interests retrieved", interestService.getCatalog(language)));
    }

    @GetMapping("/profile/interests")
    public ResponseEntity<ApiResponse<List<InterestResponse>>> mine(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        Language language = resolve(userDetails, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success("Interests retrieved", interestService.getMine(language)));
    }

    @PutMapping("/profile/interests")
    public ResponseEntity<ApiResponse<List<InterestResponse>>> replace(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
        @Valid @RequestBody UserInterestsUpdateRequest request) {
        Language language = resolve(userDetails, acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success("Interests updated", interestService.replaceMine(request, language)));
    }

    private Language resolve(UserDetails userDetails, String acceptLanguage) {
        return languageResolver.resolve(userDetails == null ? null : userDetails.getUsername(), acceptLanguage);
    }
}
