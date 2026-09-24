package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

import java.lang.reflect.Method;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserLoginHistoryResponse;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserSessionResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserSecurityControllerTest {

    private AdminUserSecurityService service;
    private AdminUserSessionRevocationService revocationService;
    private AdminUserSecurityController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminUserSecurityService.class);
        revocationService = mock(AdminUserSessionRevocationService.class);
        controller = new AdminUserSecurityController(service, revocationService);
    }

    @Test
    void returnsTheStandardEnvelopeForSessions() {
        UUID userId = UUID.randomUUID();
        PageResponse<AdminUserSessionResponse> expected = new PageResponse<>(
            java.util.List.of(), 0, 25, 0, 0, false);
        when(service.sessions(userId, 0, 25)).thenReturn(expected);

        ApiResponse<PageResponse<AdminUserSessionResponse>> response =
            controller.sessions(userId, 0, 25);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("User sessions retrieved");
        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void returnsTheStandardEnvelopeForLoginHistory() {
        UUID userId = UUID.randomUUID();
        PageResponse<AdminUserLoginHistoryResponse> expected = new PageResponse<>(
            java.util.List.of(), 0, 25, 0, 0, false);
        when(service.loginHistory(userId, 0, 25)).thenReturn(expected);

        ApiResponse<PageResponse<AdminUserLoginHistoryResponse>> response =
            controller.loginHistory(userId, 0, 25);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).isEqualTo("User login history retrieved");
        assertThat(response.getData()).isEqualTo(expected);
    }

    @Test
    void bothEndpointsRequireUserReadPermission() throws Exception {
        Method sessions = AdminUserSecurityController.class
            .getMethod("sessions", UUID.class, int.class, int.class);
        Method loginHistory = AdminUserSecurityController.class
            .getMethod("loginHistory", UUID.class, int.class, int.class);

        assertThat(sessions.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_user:read')");
        assertThat(loginHistory.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_user:read')");
    }

    @Test
    void revokesOneSessionWithTheForceLogoutPermission() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        ApiResponse<Void> response = controller.revokeSession(userId, sessionId);

        verify(revocationService).revoke(userId, sessionId);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage())
            .contains("current access token remains valid until expiry");

        Method revoke = AdminUserSecurityController.class
            .getMethod("revokeSession", UUID.class, UUID.class);
        assertThat(revoke.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_user:force_logout')");
    }
}
