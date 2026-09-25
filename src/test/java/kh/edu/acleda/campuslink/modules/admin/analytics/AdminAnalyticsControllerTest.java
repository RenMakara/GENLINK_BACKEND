package kh.edu.acleda.campuslink.modules.admin.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.request.AdminAnalyticsExportRequest;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.response.AdminAnalyticsResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

class AdminAnalyticsControllerTest {

    private AdminAnalyticsService analyticsService;
    private AdminAnalyticsExportService analyticsExportService;
    private AdminAnalyticsController controller;

    @BeforeEach
    void setUp() {
        analyticsService = mock(AdminAnalyticsService.class);
        analyticsExportService = mock(AdminAnalyticsExportService.class);
        controller = new AdminAnalyticsController(analyticsService, analyticsExportService);
    }

    @Test
    void returnsTheStandardEnvelopeAndPassesTheValidatedWindowToTheService() {
        AdminAnalyticsResponse.UserGrowth expected = new AdminAnalyticsResponse.UserGrowth(
            LocalDate.of(2026, 9, 1),
            LocalDate.of(2026, 9, 2),
            "day",
            List.of(new AdminAnalyticsResponse.SeriesPoint(LocalDate.of(2026, 9, 1), 3)));
        when(analyticsService.userGrowth(org.mockito.ArgumentMatchers.any())).thenReturn(expected);

        ApiResponse<AdminAnalyticsResponse.UserGrowth> response = controller.userGrowth(
            LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2), "day");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("User growth analytics retrieved");
        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void everyAnalyticsEndpointRequiresAnalyticsView() throws Exception {
        for (String methodName : List.of(
            "userGrowth", "activeUsers", "registrations", "userStatus", "universities",
            "workStatus", "xpDistribution", "languages", "platforms")) {
            Method method = java.util.Arrays.stream(AdminAnalyticsController.class.getMethods())
                .filter(candidate -> candidate.getName().equals(methodName))
                .findFirst()
                .orElseThrow();
            PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
            assertThat(authorization)
                .as("permission on %s", methodName)
                .isNotNull();
            assertThat(authorization.value()).isEqualTo("hasAuthority('PERM_analytics:view')");
        }
    }

    @Test
    void exportRequiresSeparatePermissionAndSudoAndReturnsCsv() throws Exception {
        AdminAnalyticsExportRequest request = new AdminAnalyticsExportRequest(
            "user-growth", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 2),
            "day", "Monthly analytics review");
        when(analyticsExportService.export(request)).thenReturn(
            "bucket,count\r\n2026-09-01,3\r\n".getBytes(java.nio.charset.StandardCharsets.UTF_8));

        ResponseEntity<byte[]> response = controller.export(request);

        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
        assertThat(response.getHeaders().getContentType()).isEqualTo(
            MediaType.parseMediaType("text/csv;charset=UTF-8"));
        assertThat(response.getHeaders().getFirst("Content-Disposition"))
            .isEqualTo("attachment; filename=\"genlink-analytics-user-growth.csv\"");
        assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
        assertThat(controller.getClass().getDeclaredMethod(
            "export", AdminAnalyticsExportRequest.class).getAnnotation(RequiresSudo.class))
            .isNotNull();
        assertThat(controller.getClass().getDeclaredMethod(
            "export", AdminAnalyticsExportRequest.class).getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_analytics:export')");
    }
}
