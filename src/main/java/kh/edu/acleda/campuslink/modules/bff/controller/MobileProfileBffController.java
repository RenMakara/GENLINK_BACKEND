package kh.edu.acleda.campuslink.modules.bff.controller;

import kh.edu.acleda.campuslink.modules.bff.dto.MobileProfileSummaryResponse;
import kh.edu.acleda.campuslink.modules.bff.service.MobileProfileBffService;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/mobile")
public class MobileProfileBffController {

    private final MobileProfileBffService mobileProfileBffService;

    public MobileProfileBffController(MobileProfileBffService mobileProfileBffService) {
        this.mobileProfileBffService = mobileProfileBffService;
    }

    @GetMapping("/profile-summary")
    public ResponseEntity<ApiResponse<MobileProfileSummaryResponse>> profileSummary(
        @AuthenticationPrincipal UserDetails userDetails) {
        MobileProfileSummaryResponse response = mobileProfileBffService.getProfileSummary(userDetails.getUsername());
        return ResponseEntity.ok(ApiResponse.success("Profile summary retrieved", response));
    }
}
