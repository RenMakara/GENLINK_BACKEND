package kh.edu.acleda.campuslink.modules.admin.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminRolePermissionUpdateRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminRoleUpdateRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminRoleRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.RolePermissionRepository;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class AdminRoleUpdateServiceTest {

    private AdminRoleRepository adminRoleRepository;
    private RolePermissionRepository rolePermissionRepository;
    private AdminPermissionCache permissionCache;
    private AdminRoleReadService readService;
    private AdminAuditService auditService;
    private AdminRoleUpdateService service;

    @BeforeEach
    void setUp() {
        adminRoleRepository = mock(AdminRoleRepository.class);
        rolePermissionRepository = mock(RolePermissionRepository.class);
        permissionCache = mock(AdminPermissionCache.class);
        readService = mock(AdminRoleReadService.class);
        auditService = mock(AdminAuditService.class);
        when(auditService.toJson(any())).thenReturn("{}");
        service = new AdminRoleUpdateService(
            adminRoleRepository,
            rolePermissionRepository,
            permissionCache,
            readService,
            auditService);
    }

    @Test
    void replacesTheWholePermissionSetInEnumOrderAndEvictsTheRoleCache() {
        UUID roleId = UUID.randomUUID();
        AdminRole role = role("MODERATOR", "Moderator", "Old description", true);
        when(adminRoleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(rolePermissionRepository.findPermissionCodesByRoleId(roleId))
            .thenReturn(List.of("user:ban", "user:read"));

        service.replacePermissions(
            roleId,
            new AdminRolePermissionUpdateRequest(
                List.of("user:ban", "dashboard:view", "user:read"),
                "Align moderator access with the approved policy"));

        verify(rolePermissionRepository).deleteByAdminRoleId(roleId);
        verify(rolePermissionRepository).saveAll(any());
        verify(permissionCache).evictRoleAuthorities(roleId);
        var audit = org.mockito.ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("ROLE_PERMISSION_UPDATE");
        assertThat(audit.getValue().targetType()).isEqualTo("ADMIN_ROLE");
        assertThat(audit.getValue().targetId()).isEqualTo(roleId);
        assertThat(audit.getValue().beforeJson()).isEqualTo("{}");
        assertThat(audit.getValue().afterJson()).isEqualTo("{}");
    }

    @Test
    void rejectsUnknownPermissionCodesWithValidationErrorBeforeWriting() {
        UUID roleId = UUID.randomUUID();
        when(adminRoleRepository.findById(roleId))
            .thenReturn(Optional.of(role("MODERATOR", "Moderator", null, true)));

        assertThatThrownBy(() -> service.replacePermissions(
            roleId,
            new AdminRolePermissionUpdateRequest(
                List.of("user:read", "not-a-real-permission"),
                "Reject an invalid permission assignment")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Unknown permission code")
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");

        verify(rolePermissionRepository, never()).deleteByAdminRoleId(roleId);
        verify(rolePermissionRepository, never()).saveAll(any());
        verify(permissionCache, never()).evictRoleAuthorities(roleId);
        verify(auditService).recordFailure(
            "ROLE_PERMISSION_UPDATE",
            "ADMIN_ROLE",
            roleId,
            "Reject an invalid permission assignment",
            "VALIDATION_ERROR");
    }

    @Test
    void neverAllowsSuperAdminPermissionRowsToBecomeAnEditableSourceOfTruth() {
        UUID roleId = UUID.randomUUID();
        when(adminRoleRepository.findById(roleId))
            .thenReturn(Optional.of(role("SUPER_ADMIN", "Super Admin", null, true)));

        assertThatThrownBy(() -> service.replacePermissions(
            roleId,
            new AdminRolePermissionUpdateRequest(
                List.of("user:read"), "Attempt to edit the implicit super admin grants")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue(
                "errorCode", "ADMIN_SUPER_ADMIN_PERMISSIONS_IMMUTABLE");

        verify(rolePermissionRepository, never()).deleteByAdminRoleId(roleId);
        verify(permissionCache, never()).evictRoleAuthorities(roleId);
        verify(auditService).recordFailure(
            "ROLE_PERMISSION_UPDATE",
            "ADMIN_ROLE",
            roleId,
            "Attempt to edit the implicit super admin grants",
            "ADMIN_SUPER_ADMIN_PERMISSIONS_IMMUTABLE");
    }

    @Test
    void updatesMetadataWithoutChangingStableCodeOrSystemFlagAndAuditsSnapshots() {
        UUID roleId = UUID.randomUUID();
        AdminRole role = role("SUPPORT", "Support", "Old description", true);
        when(adminRoleRepository.findById(roleId)).thenReturn(Optional.of(role));

        service.updateMetadata(
            roleId,
            new AdminRoleUpdateRequest(
                "  Customer Support  ",
                "  Handles verified account recovery  ",
                "Keep the support role description aligned with operations"));

        assertThat(role.getCode()).isEqualTo("SUPPORT");
        assertThat(role.getName()).isEqualTo("Customer Support");
        assertThat(role.getDescription()).isEqualTo("Handles verified account recovery");
        assertThat(role.isSystem()).isTrue();
        verify(adminRoleRepository).save(role);
        verify(permissionCache, never()).evictRoleAuthorities(roleId);
        var audit = org.mockito.ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(auditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("ROLE_UPDATE");
        assertThat(audit.getValue().beforeJson()).isEqualTo("{}");
        assertThat(audit.getValue().afterJson()).isEqualTo("{}");
    }

    private static AdminRole role(String code, String name, String description, boolean system) {
        return AdminRole.builder()
            .code(code)
            .name(name)
            .description(description)
            .isSystem(system)
            .build();
    }
}
