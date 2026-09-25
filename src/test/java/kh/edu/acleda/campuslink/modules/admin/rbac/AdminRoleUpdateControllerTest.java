package kh.edu.acleda.campuslink.modules.admin.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.lang.reflect.Method;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.auth.RequiresSudo;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminRolePermissionUpdateRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminRoleUpdateRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminRoleDetailResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.prepost.PreAuthorize;

class AdminRoleUpdateControllerTest {

    private AdminRoleUpdateService service;
    private AdminRoleUpdateController controller;

    @BeforeEach
    void setUp() {
        service = mock(AdminRoleUpdateService.class);
        controller = new AdminRoleUpdateController(service);
    }

    @Test
    void delegatesBothRoleUpdateEndpointsWithStableMessages() {
        UUID roleId = UUID.randomUUID();
        AdminRoleDetailResponse detail = new AdminRoleDetailResponse(
            roleId, "MODERATOR", "Moderator", null, true, 2, List.of("user:read"));
        AdminRolePermissionUpdateRequest permissions = new AdminRolePermissionUpdateRequest(
            List.of("user:read"), "Keep the approved moderator permissions");
        AdminRoleUpdateRequest metadata = new AdminRoleUpdateRequest(
            "Moderator", "Handles moderation", "Keep the approved moderator metadata");
        when(service.replacePermissions(roleId, permissions)).thenReturn(detail);
        when(service.updateMetadata(roleId, metadata)).thenReturn(detail);

        var permissionsResponse = controller.replacePermissions(roleId, permissions);
        var metadataResponse = controller.updateMetadata(roleId, metadata);

        verify(service).replacePermissions(roleId, permissions);
        verify(service).updateMetadata(roleId, metadata);
        assertThat(permissionsResponse.getMessage()).isEqualTo("Admin role permissions updated");
        assertThat(permissionsResponse.getData()).isSameAs(detail);
        assertThat(metadataResponse.getMessage()).isEqualTo("Admin role updated");
        assertThat(metadataResponse.getData()).isSameAs(detail);
    }

    @Test
    void bothMutationsRequireRbacManageAndSudo() throws Exception {
        assertThat(AdminRoleUpdateController.class.isAnnotationPresent(RequiresSudo.class))
            .isTrue();
        assertPermission("replacePermissions", UUID.class, AdminRolePermissionUpdateRequest.class);
        assertPermission("updateMetadata", UUID.class, AdminRoleUpdateRequest.class);
    }

    private void assertPermission(String methodName, Class<?>... parameterTypes) throws Exception {
        Method method = AdminRoleUpdateController.class.getMethod(methodName, parameterTypes);
        PreAuthorize authorization = method.getAnnotation(PreAuthorize.class);
        assertThat(authorization).isNotNull();
        assertThat(authorization.value()).isEqualTo("hasAuthority('PERM_rbac:manage')");
    }
}
