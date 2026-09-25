package kh.edu.acleda.campuslink.modules.admin.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminPermissionGroupResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminRoleDetailResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminRoleSummaryResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminRoleReadControllerTest {

    private AdminRoleReadService service;
    private AdminRoleReadController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminRoleReadService.class);
        controller = new AdminRoleReadController(service);
    }

    @Test
    void delegatesTheThreeReadEndpointsWithStandardMessages() {
        UUID roleId = UUID.randomUUID();
        List<AdminRoleSummaryResponse> roles = List.of(
            new AdminRoleSummaryResponse(roleId, "SUPPORT", "Support", null, true, 1));
        AdminRoleDetailResponse detail = new AdminRoleDetailResponse(
            roleId, "SUPPORT", "Support", null, true, 1, List.of("user:read"));
        List<AdminPermissionGroupResponse> permissions = List.of(
            new AdminPermissionGroupResponse("user", List.of()));
        when(service.listRoles()).thenReturn(roles);
        when(service.getRole(roleId)).thenReturn(detail);
        when(service.listPermissions()).thenReturn(permissions);

        var rolesResponse = controller.listRoles();
        var detailResponse = controller.getRole(roleId);
        var permissionsResponse = controller.listPermissions();

        verify(service).listRoles();
        verify(service).getRole(roleId);
        verify(service).listPermissions();
        assertThat(rolesResponse.getMessage()).isEqualTo("Admin roles retrieved");
        assertThat(rolesResponse.getData()).isSameAs(roles);
        assertThat(detailResponse.getMessage()).isEqualTo("Admin role retrieved");
        assertThat(detailResponse.getData()).isSameAs(detail);
        assertThat(permissionsResponse.getMessage()).isEqualTo("Admin permission catalogue retrieved");
        assertThat(permissionsResponse.getData()).isSameAs(permissions);
    }

    @Test
    void allReadEndpointsRequireRbacRead() throws Exception {
        assertPermission("listRoles");
        assertPermission("getRole", UUID.class);
        assertPermission("listPermissions");
    }

    private void assertPermission(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminRoleReadController.class.getMethod(methodName, parameterTypes);
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('PERM_rbac:read')");
    }
}
