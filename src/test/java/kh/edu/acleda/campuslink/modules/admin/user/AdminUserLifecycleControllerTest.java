package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserLifecycleRequest;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserLifecycleControllerTest {

    private AdminUserLifecycleService service;
    private AdminUserLifecycleController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminUserLifecycleService.class);
        controller = new AdminUserLifecycleController(service);
    }

    @Test
    void delegatesDeleteAndRestoreWithTheAuditedReasonContract() {
        UUID userId = UUID.randomUUID();
        AdminUserLifecycleRequest request =
            new AdminUserLifecycleRequest("Confirmed account recovery request");

        ApiResponse<Void> deleteResponse = controller.delete(userId, request);
        ApiResponse<Void> restoreResponse = controller.restore(userId, request);

        verify(service).delete(userId, request);
        verify(service).restore(userId, request);
        assertThat(deleteResponse.getMessage()).isEqualTo("User account deleted");
        assertThat(restoreResponse.getMessage()).isEqualTo("User account restored");
    }

    @Test
    void bothActionsRequireUserDeletePermissionAndSudo() throws Exception {
        assertThat(AdminUserLifecycleController.class.isAnnotationPresent(RequiresSudo.class))
            .isTrue();
        assertPermission("delete", UUID.class, AdminUserLifecycleRequest.class);
        assertPermission("restore", UUID.class, AdminUserLifecycleRequest.class);
    }

    private void assertPermission(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminUserLifecycleController.class.getMethod(methodName, parameterTypes);
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('PERM_user:delete')");
    }
}
