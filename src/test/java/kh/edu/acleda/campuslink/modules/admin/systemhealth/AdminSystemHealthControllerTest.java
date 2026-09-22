package kh.edu.acleda.campuslink.modules.admin.systemhealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import kh.edu.acleda.campuslink.modules.admin.systemhealth.dto.response.AdminSystemHealthResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminSystemHealthControllerTest {

    private AdminSystemHealthService healthService;
    private AdminSystemHealthController controller;

    @BeforeEach
    void setUp() {
        healthService = mock(AdminSystemHealthService.class);
        controller = new AdminSystemHealthController(healthService);
    }

    @Test
    void healthDelegatesThroughTheStandardApiResponseEnvelope() {
        AdminSystemHealthResponse expected = new AdminSystemHealthResponse(
            AdminSystemHealthResponse.Status.UP,
            Instant.parse("2026-09-23T00:00:00Z"),
            10L,
            new AdminSystemHealthResponse.ComponentHealth(AdminSystemHealthResponse.Status.UP),
            new AdminSystemHealthResponse.ComponentHealth(AdminSystemHealthResponse.Status.UP),
            new AdminSystemHealthResponse.ComponentHealth(AdminSystemHealthResponse.Status.UP),
            new AdminSystemHealthResponse.MailHealth(
                AdminSystemHealthResponse.Status.UP, false,
                AdminSystemHealthResponse.MailOutcome.NEVER_RUN),
            List.of());
        when(healthService.getHealth()).thenReturn(expected);

        ApiResponse<AdminSystemHealthResponse> response = controller.health();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Admin system health retrieved");
        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void endpointRequiresTheExistingDashboardViewPermission() throws Exception {
        Method health = AdminSystemHealthController.class.getMethod("health");
        PreAuthorize authorization = health.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('PERM_dashboard:view')");
    }
}
