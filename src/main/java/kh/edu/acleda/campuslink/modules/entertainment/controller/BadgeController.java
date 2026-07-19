package kh.edu.acleda.campuslink.modules.entertainment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.BadgeResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.BadgeService;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.localization.LanguageResolver;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entertainment/badges")
@Tag(name = "Entertainment - Badges")
public class BadgeController {

    private final BadgeService badgeService;
    private final LanguageResolver languageResolver;

    public BadgeController(BadgeService badgeService, LanguageResolver languageResolver) {
        this.badgeService = badgeService;
        this.languageResolver = languageResolver;
    }

    /** Full catalog with an earned flag per badge. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<BadgeResponse>>> getAll(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Badges retrieved",
            badgeService.getAll(language(userDetails, acceptLanguage))));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<BadgeResponse>>> getMine(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("My badges retrieved",
            badgeService.getMine(language(userDetails, acceptLanguage))));
    }

    private Language language(UserDetails userDetails, String acceptLanguage) {
        String email = userDetails == null ? null : userDetails.getUsername();
        return languageResolver.resolve(email, acceptLanguage);
    }
}
