package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.UUID;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserRecoveryControllerTest {

    private AdminUserRecoveryService service;
    private AdminUserRecoveryController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminUserRecoveryService.class);
        controller = new AdminUserRecoveryController(service);
    }

    @Test
    void delegatesAllRecoveryActionsWithNoPasswordRequestContract() {
        UUID userId = UUID.randomUUID();

        ApiResponse<Void> verifyResponse = controller.verifyEmail(userId);
        ApiResponse<Void> resetResponse = controller.resetPassword(userId);
        ApiResponse<Void> logoutResponse = controller.forceLogout(userId);

        verify(service).verifyEmail(userId);
        verify(service).resetPassword(userId);
        verify(service).forceLogout(userId);
        assertThat(verifyResponse.getMessage()).isEqualTo("User email verified");
        assertThat(resetResponse.getMessage()).isEqualTo("Password reset email requested");
        assertThat(logoutResponse.getMessage()).isEqualTo("User sessions revoked");
    }

    @Test
    void exposesTheDedicatedPermissionForEachAction() throws Exception {
        assertPermission("verifyEmail", "PERM_user:verify_email");
        assertPermission("resetPassword", "PERM_user:reset_password");
        assertPermission("forceLogout", "PERM_user:force_logout");

        assertThat(AdminUserRecoveryController.class
            .getMethod("resetPassword", UUID.class)
            .getParameterCount()).isEqualTo(1);
    }

    private void assertPermission(String methodName, String expected) throws Exception {
        Method method = AdminUserRecoveryController.class.getMethod(methodName, UUID.class);
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('" + expected + "')");
    }
}
