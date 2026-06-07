package kh.edu.acleda.campuslink.modules.mobile.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import kh.edu.acleda.campuslink.modules.bff.dto.MobileProfileSummaryResponse;
import kh.edu.acleda.campuslink.modules.bff.service.MobileProfileBffService;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileEventDetailResponse;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileFinanceDashboardResponse;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileHomeResponse;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileNotificationsResponse;
import kh.edu.acleda.campuslink.modules.mobile.dto.response.MobileProductivityDashboardResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/mobile")
@Tag(name = "Mobile BFF-lite")
public class MobileController {

    private final MobileProfileBffService mobileProfileBffService;

    public MobileController(MobileProfileBffService mobileProfileBffService) {
        this.mobileProfileBffService = mobileProfileBffService;
    }

    @GetMapping("/home")
    public ResponseEntity<ApiResponse<MobileHomeResponse>> home() {
        // TODO: Aggregate mobile home data from domain services.
        return ResponseEntity.ok(ApiResponse.success("Mobile home retrieved", new MobileHomeResponse("TODO: mobile home aggregation")));
    }

    @GetMapping("/profile-summary")
    public ResponseEntity<ApiResponse<MobileProfileSummaryResponse>> profileSummary(@AuthenticationPrincipal UserDetails userDetails) {
        return ResponseEntity.ok(ApiResponse.success("Profile summary retrieved", mobileProfileBffService.getProfileSummary(userDetails.getUsername())));
    }

    @GetMapping("/finance-dashboard")
    public ResponseEntity<ApiResponse<MobileFinanceDashboardResponse>> financeDashboard() {
        // TODO: Aggregate finance dashboard data from finance services.
        return ResponseEntity.ok(ApiResponse.success("Finance dashboard retrieved", new MobileFinanceDashboardResponse("TODO: finance dashboard aggregation")));
    }

    @GetMapping("/productivity-dashboard")
    public ResponseEntity<ApiResponse<MobileProductivityDashboardResponse>> productivityDashboard() {
        // TODO: Aggregate productivity dashboard data from productivity services.
        return ResponseEntity.ok(ApiResponse.success("Productivity dashboard retrieved", new MobileProductivityDashboardResponse("TODO: productivity dashboard aggregation")));
    }

    @GetMapping("/events/{id}")
    public ResponseEntity<ApiResponse<MobileEventDetailResponse>> eventDetail(@PathVariable UUID id) {
        // TODO: Aggregate mobile event detail from event/community services.
        return ResponseEntity.ok(ApiResponse.success("Mobile event detail retrieved", new MobileEventDetailResponse(id, "TODO: event detail aggregation")));
    }

    @GetMapping("/notifications")
    public ResponseEntity<ApiResponse<MobileNotificationsResponse>> notifications() {
        // TODO: Aggregate mobile notifications from notification service.
        return ResponseEntity.ok(ApiResponse.success("Mobile notifications retrieved", new MobileNotificationsResponse("TODO: notifications aggregation")));
    }
}
