package kh.edu.acleda.campuslink.modules.admin.sanction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminUserSanctionRequest;
import kh.edu.acleda.campuslink.modules.admin.sanction.dto.request.AdminUserSuspendRequest;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserSanctionControllerTest {

    private AdminSanctionService service;
    private AdminUserSanctionController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminSanctionService.class);
        controller = new AdminUserSanctionController(service);
    }

    @Test
    void delegatesBanAndReturnsTheStandardApiEnvelope() {
        UUID userId = UUID.randomUUID();
        AdminUserSanctionRequest request = new AdminUserSanctionRequest("Repeated abusive messages");

        ApiResponse<Void> response = controller.ban(userId, request);

        verify(service).ban(userId, request);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("User banned");
        assertThat(response.getData()).isNull();
    }

    @Test
    void delegatesUnbanAndSuspendWithTheirRequestContracts() {
        UUID userId = UUID.randomUUID();
        AdminUserSanctionRequest unban = new AdminUserSanctionRequest("Manual review completed");
        AdminUserSuspendRequest suspend = new AdminUserSuspendRequest(
            "Temporary restriction for review", 24);

        ApiResponse<Void> unbanResponse = controller.unban(userId, unban);
        ApiResponse<Void> suspendResponse = controller.suspend(userId, suspend);

        verify(service).unban(userId, unban);
        verify(service).suspend(userId, suspend);
        assertThat(unbanResponse.getMessage()).isEqualTo("User unbanned");
        assertThat(suspendResponse.getMessage()).isEqualTo("User suspended");
    }

    @Test
    void exposesTheRequiredPermissionForEachEndpoint() throws Exception {
        assertPermission("ban", "PERM_user:ban");
        assertPermission("unban", "PERM_user:unban");
        assertPermission("suspend", "PERM_user:suspend");
    }

    private void assertPermission(String methodName, String permission) throws Exception {
        Method method = switch (methodName) {
            case "ban", "unban" -> AdminUserSanctionController.class.getMethod(
                methodName, UUID.class, AdminUserSanctionRequest.class);
            case "suspend" -> AdminUserSanctionController.class.getMethod(
                methodName, UUID.class, AdminUserSuspendRequest.class);
            default -> throw new IllegalArgumentException(methodName);
        };
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);

        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('PERM_" + permission.substring(5) + "')");
    }
}
