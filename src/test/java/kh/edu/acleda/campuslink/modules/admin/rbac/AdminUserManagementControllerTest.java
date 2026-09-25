package kh.edu.acleda.campuslink.modules.admin.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminDeactivationRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminPromotionRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminRoleChangeRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminStaffResponse;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminUserManagementControllerTest {

    private AdminUserManagementService service;
    private AdminUserManagementController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminUserManagementService.class);
        controller = new AdminUserManagementController(service);
    }

    @Test
    void delegatesTheDirectoryAndThreeMutationsWithStableMessages() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        AdminPromotionRequest promote = new AdminPromotionRequest(
            userId, roleId, "Promote the approved staff member");
        AdminRoleChangeRequest change = new AdminRoleChangeRequest(
            roleId, "Move the staff member to customer support");
        AdminDeactivationRequest deactivate = new AdminDeactivationRequest(
            "Revoke staff access after the contract ended");
        AdminStaffResponse staff = new AdminStaffResponse(
            adminId, userId, "Staff", "staff", "staff@genlink.kh", roleId,
            "SUPPORT", "Support", true, true, true, null, null, null);
        PageResponse<AdminStaffResponse> page = new PageResponse<>(List.of(staff), 0, 25, 1, 1, false);
        when(service.list(0, 25)).thenReturn(page);
        when(service.promote(promote)).thenReturn(staff);
        when(service.changeRole(adminId, change)).thenReturn(staff);
        when(service.deactivate(adminId, deactivate)).thenReturn(staff);

        var listResponse = controller.list(0, 25);
        var promoteResponse = controller.promote(promote);
        var changeResponse = controller.changeRole(adminId, change);
        var deactivateResponse = controller.deactivate(adminId, deactivate);

        verify(service).list(0, 25);
        verify(service).promote(promote);
        verify(service).changeRole(adminId, change);
        verify(service).deactivate(adminId, deactivate);
        assertThat(listResponse.getMessage()).isEqualTo("Admin users retrieved");
        assertThat(promoteResponse.getMessage()).isEqualTo("User promoted to admin");
        assertThat(changeResponse.getMessage()).isEqualTo("Admin role changed");
        assertThat(deactivateResponse.getMessage()).isEqualTo("Admin deactivated");
    }

    @Test
    void listRequiresReadAndMutationsRequireManageAndSudo() throws Exception {
        assertPermission("list", int.class, int.class, "hasAuthority('PERM_rbac:read')");
        assertThat(AdminUserManagementController.class.isAnnotationPresent(RequiresSudo.class))
            .isFalse();
        assertMutation("promote", AdminPromotionRequest.class);
        assertMutation("changeRole", UUID.class, AdminRoleChangeRequest.class);
        assertMutation("deactivate", UUID.class, AdminDeactivationRequest.class);
    }

    private void assertMutation(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminUserManagementController.class.getMethod(methodName, parameterTypes);
        assertThat(method.getAnnotation(RequiresSudo.class)).isNotNull();
        assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(method.getAnnotation(PreAuthorize.class).value())
            .isEqualTo("hasAuthority('PERM_rbac:manage')");
    }

    private void assertPermission(
        String methodName, Class<?> first, Class<?> second, String expected) throws Exception {
        Method method = AdminUserManagementController.class.getMethod(methodName, first, second);
        assertThat(method.getAnnotation(PreAuthorize.class)).isNotNull();
        assertThat(method.getAnnotation(PreAuthorize.class).value()).isEqualTo(expected);
    }
}
