package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserXpAdjustmentRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserXpAdjustmentResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserXpAdjustmentControllerTest {

    private AdminUserXpAdjustmentService service;
    private AdminUserXpAdjustmentController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminUserXpAdjustmentService.class);
        controller = new AdminUserXpAdjustmentController(service);
    }

    @Test
    void returnsTheAdjustmentResultInTheStandardApiEnvelope() {
        UUID userId = UUID.randomUUID();
        AdminUserXpAdjustmentRequest request =
            new AdminUserXpAdjustmentRequest(200, "Support correction after review");
        AdminUserXpAdjustmentResponse expected =
            new AdminUserXpAdjustmentResponse(userId, 200, 200, 400, 5);
        when(service.adjust(userId, request)).thenReturn(expected);

        ApiResponse<AdminUserXpAdjustmentResponse> response = controller.adjust(userId, request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("User XP adjusted");
        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void allowsEitherDirectionalPermissionAtTheHttpBoundary() throws Exception {
        Method adjust = AdminUserXpAdjustmentController.class.getMethod(
            "adjust", UUID.class, AdminUserXpAdjustmentRequest.class);
        PreAuthorize authorization = adjust.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value())
            .isEqualTo("hasAnyAuthority('PERM_xp:grant', 'PERM_xp:revoke')");
    }
}
