package kh.edu.acleda.campuslink.modules.admin.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminPermissionGroupResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminRoleRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.RolePermissionRepository;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminRoleReadServiceTest {

    private AdminRoleRepository adminRoleRepository;
    private AdminUserRepository adminUserRepository;
    private RolePermissionRepository rolePermissionRepository;
    private AdminRoleReadService service;

    @BeforeEach
    void setUp() {
        adminRoleRepository = mock(AdminRoleRepository.class);
        adminUserRepository = mock(AdminUserRepository.class);
        rolePermissionRepository = mock(RolePermissionRepository.class);
        service = new AdminRoleReadService(
            adminRoleRepository, adminUserRepository, rolePermissionRepository);
    }

    @Test
    void listsRolesWithActiveAdminCountsAndStableCodeOrdering() {
        UUID moderatorId = UUID.randomUUID();
        UUID supportId = UUID.randomUUID();
        AdminRole moderator = role(moderatorId, "MODERATOR", "Moderator", true);
        AdminRole support = role(supportId, "SUPPORT", "Support", true);
        AdminRoleRepository.ActiveAdminCount count = mock(AdminRoleRepository.ActiveAdminCount.class);
        when(count.getRoleId()).thenReturn(moderatorId);
        when(count.getAdminCount()).thenReturn(2L);
        when(adminRoleRepository.findAllByOrderByCodeAsc()).thenReturn(List.of(moderator, support));
        when(adminRoleRepository.countActiveAdminsByRole()).thenReturn(List.of(count));

        var result = service.listRoles();

        assertThat(result).containsExactly(
            new kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminRoleSummaryResponse(
                moderatorId, "MODERATOR", "Moderator", null, true, 2),
            new kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminRoleSummaryResponse(
                supportId, "SUPPORT", "Support", null, true, 0));
    }

    @Test
    void superAdminGetsEveryEnumPermissionEvenWithoutStoredGrants() {
        UUID roleId = UUID.randomUUID();
        AdminRole superAdmin = role(roleId, "SUPER_ADMIN", "Super Admin", true);
        when(adminRoleRepository.findById(roleId)).thenReturn(Optional.of(superAdmin));
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(1L);

        var result = service.getRole(roleId);

        assertThat(result.permissions()).containsExactlyElementsOf(Permission.allCodes());
        verify(rolePermissionRepository, org.mockito.Mockito.never()).findPermissionCodesByRoleId(roleId);
    }

    @Test
    void customRoleReturnsKnownPermissionsInCatalogueOrderAndOmitsStaleCodes() {
        UUID roleId = UUID.randomUUID();
        AdminRole role = role(roleId, "MODERATOR", "Moderator", true);
        when(adminRoleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("MODERATOR"))
            .thenReturn(3L);
        when(rolePermissionRepository.findPermissionCodesByRoleId(roleId))
            .thenReturn(List.of("user:ban", "stale:permission", "user:read"));

        var result = service.getRole(roleId);

        assertThat(result.adminCount()).isEqualTo(3);
        assertThat(result.permissions()).containsExactly("user:read", "user:ban");
    }

    @Test
    void groupsThePermissionEnumByResourceWithDescriptions() {
        var groups = service.listPermissions();

        assertThat(groups.get(0).resource()).isEqualTo("dashboard");
        AdminPermissionGroupResponse userGroup = groups.stream()
            .filter(group -> group.resource().equals("user"))
            .findFirst()
            .orElseThrow();
        assertThat(userGroup.permissions()).anySatisfy(permission -> {
            assertThat(permission.code()).isEqualTo("user:export:pii");
            assertThat(permission.resource()).isEqualTo("user");
            assertThat(permission.action()).isEqualTo("export:pii");
            assertThat(permission.description()).isNotBlank();
        });
    }

    @Test
    void missingRoleIsReportedAsNotFound() {
        UUID roleId = UUID.randomUUID();
        when(adminRoleRepository.findById(roleId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getRole(roleId))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("Admin role not found");
    }

    private static AdminRole role(UUID id, String code, String name, boolean system) {
        AdminRole role = mock(AdminRole.class);
        when(role.getId()).thenReturn(id);
        when(role.getCode()).thenReturn(code);
        when(role.getName()).thenReturn(name);
        when(role.isSystem()).thenReturn(system);
        return role;
    }
}
