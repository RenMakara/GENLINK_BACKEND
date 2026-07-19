package kh.edu.acleda.campuslink.modules.entertainment.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.entertainment.dto.response.ChallengeResponse;
import kh.edu.acleda.campuslink.modules.entertainment.service.ChallengeService;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.localization.LanguageResolver;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/entertainment/challenges")
@Tag(name = "Entertainment - Challenges")
public class ChallengeController {

    private final ChallengeService challengeService;
    private final LanguageResolver languageResolver;

    public ChallengeController(ChallengeService challengeService, LanguageResolver languageResolver) {
        this.challengeService = challengeService;
        this.languageResolver = languageResolver;
    }

    /** Active challenges, each with the user's status. */
    @GetMapping
    public ResponseEntity<ApiResponse<List<ChallengeResponse>>> getActive(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Challenges retrieved",
            challengeService.getActive(language(userDetails, acceptLanguage))));
    }

    @GetMapping("/mine")
    public ResponseEntity<ApiResponse<List<ChallengeResponse>>> getMine(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("My challenges retrieved",
            challengeService.getMine(language(userDetails, acceptLanguage))));
    }

    @PostMapping("/{id}/join")
    public ResponseEntity<ApiResponse<ChallengeResponse>> join(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Challenge joined",
            challengeService.join(id, language(userDetails, acceptLanguage))));
    }

    @PostMapping("/{id}/complete")
    public ResponseEntity<ApiResponse<ChallengeResponse>> complete(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Challenge completed",
            challengeService.complete(id, language(userDetails, acceptLanguage))));
    }

    @PostMapping("/{id}/claim")
    public ResponseEntity<ApiResponse<ChallengeResponse>> claim(
        @PathVariable UUID id,
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        return ResponseEntity.ok(ApiResponse.success("Reward claimed",
            challengeService.claim(id, language(userDetails, acceptLanguage))));
    }

    private Language language(UserDetails userDetails, String acceptLanguage) {
        String email = userDetails == null ? null : userDetails.getUsername();
        return languageResolver.resolve(email, acceptLanguage);
    }
}
