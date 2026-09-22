package kh.edu.acleda.campuslink.modules.admin.dashboard;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import kh.edu.acleda.campuslink.modules.admin.dashboard.dto.response.AdminDashboardSummaryResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** The permission-gated HTTP boundary for the Phase 2 admin overview. */
@Tag(name = "Admin - Dashboard", description = "Overview metrics and recent admin activity")
@RestController
@RequestMapping("/api/v1/admin/dashboard")
@RequiredArgsConstructor
public class AdminDashboardController {

    private final AdminDashboardService adminDashboardService;

    @Operation(
        summary = "Get the admin dashboard summary",
        description = "Returns KPI cards, chart series and recent activity. Each section may be "
            + "null when its backing aggregate is temporarily unavailable.")
    @GetMapping("/summary")
    @PreAuthorize("hasAuthority('PERM_dashboard:view')")
    public ApiResponse<AdminDashboardSummaryResponse> summary() {
        return ApiResponse.success("Admin dashboard summary retrieved", adminDashboardService.getSummary());
    }
}
