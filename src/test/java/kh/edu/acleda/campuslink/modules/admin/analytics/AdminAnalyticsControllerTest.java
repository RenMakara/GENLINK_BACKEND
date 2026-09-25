package kh.edu.acleda.campuslink.modules.admin.analytics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.LocalDate;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.analytics.dto.response.AdminAnalyticsResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminAnalyticsControllerTest {

    private AdminAnalyticsService analyticsService;
    private AdminAnalyticsController controller;

    @BeforeEach
    void setUp() {
        analyticsService = mock(AdminAnalyticsService.class);
        controller = new AdminAnalyticsController(analyticsService);
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
}

