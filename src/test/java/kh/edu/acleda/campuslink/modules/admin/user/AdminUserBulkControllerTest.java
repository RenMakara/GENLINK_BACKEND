package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminBulkBadgeRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminBulkBanRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminBulkNotifyRequest;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminBulkActionResponse;
import kh.edu.acleda.campuslink.shared.response.ApiResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserBulkControllerTest {

    private static final UUID BATCH_ID = UUID.randomUUID();
    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID BADGE_ID = UUID.randomUUID();

    private AdminUserBulkService service;
    private AdminUserBulkController controller;
    private AdminBulkActionResponse result;

    @BeforeEach
    void setUp() {
        service = mock(AdminUserBulkService.class);
        controller = new AdminUserBulkController(service);
        result = new AdminBulkActionResponse(BATCH_ID, 1, 1, List.of());
        when(service.ban(org.mockito.ArgumentMatchers.any())).thenReturn(result);
        when(service.notify(org.mockito.ArgumentMatchers.any())).thenReturn(result);
        when(service.assignBadge(org.mockito.ArgumentMatchers.any())).thenReturn(result);
    }

    @Test
    void delegatesAllThreeActionsUsingTheSharedResponseEnvelope() {
        AdminBulkBanRequest banRequest = new AdminBulkBanRequest(
            BATCH_ID, List.of(USER_ID), "Repeated abuse confirmed");
        AdminBulkNotifyRequest notifyRequest = new AdminBulkNotifyRequest(
            BATCH_ID, List.of(USER_ID), "ADMIN_NOTICE", Map.of(), "Important service notice");
        AdminBulkBadgeRequest badgeRequest = new AdminBulkBadgeRequest(
            BATCH_ID, List.of(USER_ID), BADGE_ID, "Manual recognition approved");

        ApiResponse<AdminBulkActionResponse> ban = controller.ban(banRequest);
        ApiResponse<AdminBulkActionResponse> notify = controller.notify(notifyRequest);
        ApiResponse<AdminBulkActionResponse> badge = controller.assignBadge(badgeRequest);

        verify(service).ban(banRequest);
        verify(service).notify(notifyRequest);
        verify(service).assignBadge(badgeRequest);
        assertThat(ban.getData()).isEqualTo(result);
        assertThat(notify.getData()).isEqualTo(result);
        assertThat(badge.getData()).isEqualTo(result);
    }

    @Test
    void exposesTheExpectedPermissionsAndSudoGuard() throws Exception {
        assertPermission("ban", "PERM_user:ban");
        assertPermission("notify", "PERM_notification:send");
        assertPermission("assignBadge", "PERM_badge:assign");

        Method ban = AdminUserBulkController.class.getMethod("ban", AdminBulkBanRequest.class);
        assertThat(ban.getAnnotation(RequiresSudo.class)).isNotNull();
        assertThat(AdminUserBulkController.class
            .getMethod("notify", AdminBulkNotifyRequest.class)
            .getAnnotation(RequiresSudo.class)).isNull();
    }

    private void assertPermission(String methodName, String expected) throws Exception {
        Method method = switch (methodName) {
            case "ban" -> AdminUserBulkController.class.getMethod(methodName, AdminBulkBanRequest.class);
            case "notify" -> AdminUserBulkController.class.getMethod(methodName, AdminBulkNotifyRequest.class);
            case "assignBadge" -> AdminUserBulkController.class.getMethod(methodName, AdminBulkBadgeRequest.class);
            default -> throw new IllegalArgumentException(methodName);
        };
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('" + expected + "')");
    }
}
