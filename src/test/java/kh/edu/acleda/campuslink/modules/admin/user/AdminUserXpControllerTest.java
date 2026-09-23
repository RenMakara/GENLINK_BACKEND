package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserXpResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserXpControllerTest {

    private AdminUserXpService service;
    private AdminUserXpController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminUserXpService.class);
        controller = new AdminUserXpController(service);
    }

    @Test
    void returnsTheStandardApiEnvelope() {
        UUID userId = UUID.randomUUID();
        AdminUserXpResponse expected = mock(AdminUserXpResponse.class);
        when(service.get(userId, 0, 25)).thenReturn(expected);

        ApiResponse<AdminUserXpResponse> response = controller.get(userId, 0, 25);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("User XP detail retrieved");
        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void requiresUserReadPermission() throws Exception {
        Method get = AdminUserXpController.class.getMethod("get", UUID.class, int.class, int.class);
        PreAuthorize authorization = get.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('PERM_user:read')");
    }
}
