package kh.edu.acleda.campuslink.modules.mobile.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kh.edu.acleda.campuslink.modules.bff.dto.MobileProfileSummaryResponse;
import kh.edu.acleda.campuslink.modules.bff.service.MobileFinanceBffService;
import kh.edu.acleda.campuslink.modules.bff.service.MobileHomeBffService;
import kh.edu.acleda.campuslink.modules.bff.service.MobileProductivityBffService;
import kh.edu.acleda.campuslink.modules.bff.service.MobileProfileBffService;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileEventDetailResponse;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileFinanceDashboardResponse;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileHomeResponse;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileNotificationsResponse;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileProductivityDashboardResponse;
import kh.edu.acleda.campuslink.modules.finance.enums.Currency;
import kh.edu.acleda.campuslink.shared.enums.Language;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.localization.LanguageResolver;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mobile")
@Tag(name = "Mobile BFF-lite")
public class MobileController {

    private final MobileProfileBffService mobileProfileBffService;
    private final MobileHomeBffService mobileHomeBffService;
    private final MobileFinanceBffService mobileFinanceBffService;
    private final MobileProductivityBffService mobileProductivityBffService;
    private final LanguageResolver languageResolver;

    public MobileController(MobileProfileBffService mobileProfileBffService,
                            MobileHomeBffService mobileHomeBffService,
                            MobileFinanceBffService mobileFinanceBffService,
                            MobileProductivityBffService mobileProductivityBffService,
                            LanguageResolver languageResolver) {
        this.mobileProfileBffService = mobileProfileBffService;
        this.mobileHomeBffService = mobileHomeBffService;
        this.mobileFinanceBffService = mobileFinanceBffService;
        this.mobileProductivityBffService = mobileProductivityBffService;
        this.languageResolver = languageResolver;
    }

    /** Home Super Dashboard: every card in one call. */
    @GetMapping("/home")
    public ResponseEntity<ApiResponse<MobileHomeResponse>> home(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage,
        @RequestParam(defaultValue = "USD") Currency currency) {
        Language language = languageResolver.resolve(userDetails.getUsername(), acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success("Mobile home retrieved",
            mobileHomeBffService.getHome(userDetails.getUsername(), language, currency)));
    }

    @GetMapping("/profile-summary")
    public ResponseEntity<ApiResponse<MobileProfileSummaryResponse>> profileSummary(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Profile summary retrieved", mobileProfileBffService.getProfileSummary(userDetails.getUsername())));
    }

    @GetMapping("/finance-dashboard")
    public ResponseEntity<ApiResponse<MobileFinanceDashboardResponse>> financeDashboard(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestParam(defaultValue = "USD") Currency currency) {
        return ResponseEntity.ok(ApiResponse.success("Finance dashboard retrieved",
            mobileFinanceBffService.getFinanceDashboard(userDetails.getUsername(), currency)));
    }

    @GetMapping("/productivity-dashboard")
    public ResponseEntity<ApiResponse<MobileProductivityDashboardResponse>> productivityDashboard(
        @AuthenticationPrincipal UserDetails userDetails,
        @RequestHeader(value = HttpHeaders.ACCEPT_LANGUAGE, required = false) String acceptLanguage) {
        Language language = languageResolver.resolve(userDetails.getUsername(), acceptLanguage);
        return ResponseEntity.ok(ApiResponse.success("Productivity dashboard retrieved",
            mobileProductivityBffService.getDashboard(userDetails.getUsername(), language)));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<ApiResponse<MobileEventDetailResponse>> eventDetail(@PathVariable UUID id) {
        throw new ApiException("Mobile event detail aggregation is currently not available.", "NOT_IMPLEMENTED");
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<MobileNotificationsResponse>> notifications() {
        throw new ApiException("Mobile notifications aggregation is currently not available.", "NOT_IMPLEMENTED");
    }
}
