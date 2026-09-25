package kh.edu.acleda.campuslink.modules.admin.analytics;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.time.LocalDate;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsExportRequest;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsSeries;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsQuery;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.response.AdminAnalyticsResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Permission-gated read boundary for the fixed Phase 6 analytics dashboards. */
@Tag(name = "Admin - Analytics", description = "Fixed product analytics aggregates")
@RestController
@RequestMapping("/api/v1/admin/analytics")
@RequiredArgsConstructor
public class AdminAnalyticsController {

    private final AdminAnalyticsService analyticsService;
    private final AdminAnalyticsExportService analyticsExportService;

    @Operation(summary = "Get cumulative user growth", description = "Counts users by Phnom Penh bucket.")
    @GetMapping("/user-growth")
    @PreAuthorize("hasAuthority('PERM_analytics:view')")
    public ApiResponse<AdminAnalyticsResponse.UserGrowth> userGrowth(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String granularity) {
        return ApiResponse.success(
            "User growth analytics retrieved",
            analyticsService.userGrowth(query(from, to, granularity)));
    }

    @Operation(summary = "Get active-user analytics", description = "Returns estimated DAU and MAU series.")
    @GetMapping("/active-users")
    @PreAuthorize("hasAuthority('PERM_analytics:view')")
    public ApiResponse<AdminAnalyticsResponse.ActiveUsers> activeUsers(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String granularity) {
        return ApiResponse.success(
            "Active-user analytics retrieved",
            analyticsService.activeUsers(query(from, to, granularity)));
    }

    @Operation(summary = "Get registration analytics", description = "Returns signup counts split by auth provider.")
    @GetMapping("/registrations")
    @PreAuthorize("hasAuthority('PERM_analytics:view')")
    public ApiResponse<AdminAnalyticsResponse.Registrations> registrations(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String granularity) {
        return ApiResponse.success(
            "Registration analytics retrieved",
            analyticsService.registrations(query(from, to, granularity)));
    }

    @Operation(summary = "Get user-status analytics", description = "Returns account-status distribution and trend.")
    @GetMapping("/user-status")
    @PreAuthorize("hasAuthority('PERM_analytics:view')")
    public ApiResponse<AdminAnalyticsResponse.Distribution> userStatus(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String granularity) {
        return ApiResponse.success(
            "User-status analytics retrieved",
            analyticsService.userStatus(query(from, to, granularity)));
    }

    @Operation(summary = "Get university analytics", description = "Returns the top 20 universities and Other.")
    @GetMapping("/universities")
    @PreAuthorize("hasAuthority('PERM_analytics:view')")
    public ApiResponse<AdminAnalyticsResponse.Distribution> universities(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String granularity) {
        return ApiResponse.success(
            "University analytics retrieved",
            analyticsService.universities(query(from, to, granularity)));
    }

    @Operation(summary = "Get work-status analytics", description = "Returns the fixed work-status distribution.")
    @GetMapping("/work-status")
    @PreAuthorize("hasAuthority('PERM_analytics:view')")
    public ApiResponse<AdminAnalyticsResponse.Distribution> workStatus(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String granularity) {
        return ApiResponse.success(
            "Work-status analytics retrieved",
            analyticsService.workStatus(query(from, to, granularity)));
    }

    @Operation(summary = "Get XP distribution analytics", description = "Returns XP buckets and level counts.")
    @GetMapping("/xp-distribution")
    @PreAuthorize("hasAuthority('PERM_analytics:view')")
    public ApiResponse<AdminAnalyticsResponse.XpDistribution> xpDistribution(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String granularity) {
        return ApiResponse.success(
            "XP distribution analytics retrieved",
            analyticsService.xpDistribution(query(from, to, granularity)));
    }

    @Operation(summary = "Get language analytics", description = "Returns preferred-language distribution and trend.")
    @GetMapping("/languages")
    @PreAuthorize("hasAuthority('PERM_analytics:view')")
    public ApiResponse<AdminAnalyticsResponse.Distribution> languages(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String granularity) {
        return ApiResponse.success(
            "Language analytics retrieved",
            analyticsService.languages(query(from, to, granularity)));
    }

    @Operation(summary = "Get platform analytics", description = "Returns an approximate platform split with Unknown retained.")
    @GetMapping("/platforms")
    @PreAuthorize("hasAuthority('PERM_analytics:view')")
    public ApiResponse<AdminAnalyticsResponse.Distribution> platforms(
        @RequestParam(required = false) LocalDate from,
        @RequestParam(required = false) LocalDate to,
        @RequestParam(required = false) String granularity) {
        return ApiResponse.success(
            "Platform analytics retrieved",
            analyticsService.platforms(query(from, to, granularity)));
    }

    @Operation(
        summary = "Export one analytics series as CSV",
        description = "Exports one of the nine analytics series for the requested Phnom Penh date range. "
            + "Requires analytics:export and a fresh sudo window.")
    @PostMapping(value = "/export", consumes = MediaType.APPLICATION_JSON_VALUE, produces = "text/csv")
    @RequiresSudo
    @PreAuthorize("hasAuthority('PERM_analytics:export')")
    public ResponseEntity<byte[]> export(@Valid @RequestBody AdminAnalyticsExportRequest request) {
        byte[] csv = analyticsExportService.export(request);
        String series = AdminAnalyticsSeries.fromValue(request.series()).wireValue();
        return ResponseEntity.ok()
            .contentType(MediaType.parseMediaType("text/csv;charset=UTF-8"))
            .header(HttpHeaders.CONTENT_DISPOSITION,
                "attachment; filename=\"genlink-analytics-" + series + ".csv\"")
            .header(HttpHeaders.CACHE_CONTROL, "no-store")
            .body(csv);
    }

    private static AdminAnalyticsQuery query(
        LocalDate from,
        LocalDate to,
        String granularity) {
        return AdminAnalyticsQuery.fromRequest(from, to, granularity);
    }
}
