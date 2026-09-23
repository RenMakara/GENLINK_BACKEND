package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserActivityResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserActivityControllerTest {

    private AdminUserActivityService service;
    private AdminUserActivityController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminUserActivityService.class);
        controller = new AdminUserActivityController(service);
    }

    @Test
    void returnsTheStandardApiEnvelope() {
        UUID userId = UUID.randomUUID();
        AdminUserActivityResponse expected = mock(AdminUserActivityResponse.class);
        when(service.get(userId)).thenReturn(expected);

        ApiResponse<AdminUserActivityResponse> response = controller.get(userId);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("User activity counts retrieved");
        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void requiresUserReadPermission() throws Exception {
        Method get = AdminUserActivityController.class.getMethod("get", UUID.class);
        PreAuthorize authorization = get.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('PERM_user:read')");
    }
}
