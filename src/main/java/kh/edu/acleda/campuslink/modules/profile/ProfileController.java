package kh.edu.acleda.campuslink.modules.profile;

import jakarta.validation.Valid;
import kh.edu.acleda.campuslink.modules.media.MediaService;
import kh.edu.acleda.campuslink.modules.profile.dto.ProfileResponse;
import kh.edu.acleda.campuslink.modules.profile.dto.ProfileUpdateRequest;
import kh.edu.acleda.campuslink.modules.profile.dto.PublicProfileResponse;
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
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/profile")
public class ProfileController {

    private final ProfileService profileService;
    private final LanguageResolver languageResolver;
    private final MediaService mediaService;

    public ProfileController(ProfileService profileService, LanguageResolver languageResolver,
                             MediaService mediaService) {
        this.profileService = profileService;
        this.languageResolver = languageResolver;
        this.mediaService = mediaService;
    }

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> me(@AuthenticationPrincipal UserDetails userDetails) {
        ProfileResponse response = profileService.getCurrentProfile(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Profile retrieved", response));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateMe(
        @AuthenticationPrincipal UserDetails userDetails,
        @Valid @RequestBody ProfileUpdateRequest request) {
        ProfileResponse response = profileService.updateCurrentProfile(userDetails.getUsername(), request);
        return ResponseEntity.ok(ApiResponse.success("Profile updated", response));
    }

    /** Set the avatar. */
    @PostMapping("/avatar")
    public ResponseEntity<ApiResponse<ProfileResponse>> updateAvatar(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestBody Map<String, String> request) {
        String mediaId = request.get("avatarMediaId");
        String avatarUrl = mediaId != null && !mediaId.isBlank()
            ? mediaService.resolveConfirmedUrl(UUID.fromString(mediaId))
            : request.get("avatarUrl");
        ProfileResponse response = profileService.updateAvatar(userDetails.getUsername(), avatarUrl);
        return ResponseEntity.ok(ApiResponse.success("Avatar updated", response));
    }

    /** Nested under /public/ so a username can never collide with the sibling literal routes (/me, /settings, /portfolio). */
    @GetMapping("/public/{username}")
    public ResponseEntity<ApiResponse<PublicProfileResponse>> publicProfile(
        @AuthenticationPrincipal UserDetails userDetails,
        @PathVariable String username,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        Language language = languageResolver.resolve(
            userDetails == null ? null : userDetails.getUsername(), acceptLanguage);
        PublicProfileResponse response = profileService.getPublicProfile(username, language);
        return ResponseEntity.ok(ApiResponse.success("Public profile retrieved", response));
    }
}
