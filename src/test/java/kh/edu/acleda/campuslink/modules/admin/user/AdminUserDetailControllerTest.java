package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserDetailResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserDetailControllerTest {

    private AdminUserDetailService service;
    private AdminUserDetailController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminUserDetailService.class);
        controller = new AdminUserDetailController(service);
    }

    @Test
    void returnsTheStandardApiEnvelope() {
        UUID userId = UUID.randomUUID();
        AdminUserDetailResponse expected = mock(AdminUserDetailResponse.class);
        when(service.get(userId)).thenReturn(expected);

        ApiResponse<AdminUserDetailResponse> response = controller.get(userId);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("Admin user retrieved");
        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void requiresUserReadPermission() throws Exception {
        Method get = AdminUserDetailController.class.getMethod("get", UUID.class);
        PreAuthorize authorization = get.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('PERM_user:read')");
    }
}
