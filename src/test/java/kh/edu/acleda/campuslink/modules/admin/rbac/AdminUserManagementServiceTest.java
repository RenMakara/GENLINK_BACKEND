package kh.edu.acleda.campuslink.modules.admin.rbac;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.lenient;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminDeactivationRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminMfaResetRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminPromotionRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminRoleChangeRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminRoleRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.Role;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.data.domain.PageImpl;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

@ExtendWith(MockitoExtension.class)
class AdminUserManagementServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");

    @Mock private AdminUserRepository adminUserRepository;
    @Mock private AdminRoleRepository adminRoleRepository;
    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private AdminPermissionCache adminPermissionCache;
    @Mock private SuperAdminGuard superAdminGuard;
    @Mock private AdminAuditService adminAuditService;

    private AdminUserManagementService service;
    private AdminUser actor;

    @BeforeEach
    void setUp() {
        service = new AdminUserManagementService(
            adminUserRepository,
            adminRoleRepository,
            userRepository,
            refreshTokenService,
            adminPermissionCache,
            superAdminGuard,
            adminAuditService,
            Clock.fixed(NOW, ZoneOffset.UTC));
        actor = admin("owner@genlink.kh", "SUPER_ADMIN", true);
        lenient().when(adminUserRepository.findActiveByUserEmail("owner@genlink.kh"))
            .thenReturn(Optional.of(actor));
        lenient().when(adminAuditService.toJson(any())).thenReturn("{}");
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(
                "owner@genlink.kh", "session", List.of()));
    }

    @AfterEach
    void clearSecurityContext() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void listsActiveAndRevokedStaffWithoutReturningCredentialFields() {
        User user = user("staff@genlink.kh", "staff", "Staff User");
        AdminUser staff = admin(user, "MODERATOR", true);
        AdminUser revoked = admin(user("former@genlink.kh", "former", "Former Staff"), "SUPPORT", false);
        revoked.setDeletedAt(NOW);
        when(adminUserRepository.findAll(any(Pageable.class)))
            .thenReturn(new PageImpl<>(List.of(staff, revoked)));

        var result = service.list(0, 25);

        assertThat(result.content()).hasSize(2);
        assertThat(result.content().get(0).email()).isEqualTo("staff@genlink.kh");
        assertThat(result.content().get(0).roleCode()).isEqualTo("MODERATOR");
        assertThat(result.content().get(0).active()).isTrue();
        assertThat(result.content().get(1).active()).isFalse();
        assertThat(result.content().get(1).deletedAt()).isEqualTo(NOW);
        assertThat(result.content().get(0)).hasNoNullFieldsOrPropertiesExcept(
            "adminId", "userId", "roleId", "lastLoginAt", "createdAt", "deletedAt");
    }

    @Test
    void promotionCreatesAdminProfileSyncsCoarseRoleAndInvalidatesIdentityCache() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        User user = user("new@genlink.kh", "new-admin", "New Admin");
        AdminRole role = role("MODERATOR", "Moderator");
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(adminRoleRepository.findById(roleId)).thenReturn(Optional.of(role));
        when(adminUserRepository.existsByUserId(userId)).thenReturn(false);
        when(adminUserRepository.save(any(AdminUser.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.promote(new AdminPromotionRequest(
            userId, roleId, "Promote the approved moderation staff member"));

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(result.email()).isEqualTo("new@genlink.kh");
        assertThat(result.roleCode()).isEqualTo("MODERATOR");
        assertThat(result.active()).isTrue();
        verify(userRepository).save(user);
        verify(adminPermissionCache).evictIdentity("new@genlink.kh");
        verify(refreshTokenService, never()).revoke(user);
        verify(adminAuditService).record(any(AdminAuditEvent.class));
    }

    @Test
    void promotionRejectsUnverifiedUsersBeforeWritingAnAdminProfile() {
        UUID userId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        User user = user("pending@genlink.kh", "pending", "Pending User");
        user.setAccountStatus(AccountStatus.PENDING);
        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(user));
        when(adminRoleRepository.findById(roleId))
            .thenReturn(Optional.of(role("SUPPORT", "Support")));

        assertThatThrownBy(() -> service.promote(new AdminPromotionRequest(
            userId, roleId, "Reject a user who has not completed verification")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");

        verify(adminUserRepository, never()).save(any(AdminUser.class));
        verify(userRepository, never()).save(user);
        verify(adminPermissionCache, never()).evictIdentity(any());
    }

    @Test
    void roleChangeRevokesSessionsBumpsTokenVersionAndEvictsIdentityCache() {
        UUID adminId = UUID.randomUUID();
        UUID roleId = UUID.randomUUID();
        User user = user("staff@genlink.kh", "staff", "Staff User");
        user.setRole(Role.ADMIN);
        AdminUser target = admin(user, "MODERATOR", true);
        AdminRole newRole = role("SUPPORT", "Support");
        when(adminUserRepository.findByIdForUpdate(adminId)).thenReturn(Optional.of(target));
        when(adminRoleRepository.findById(roleId)).thenReturn(Optional.of(newRole));
        when(adminUserRepository.save(any(AdminUser.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.changeRole(
            adminId,
            new AdminRoleChangeRequest(roleId, "Move the staff member to customer support"));

        assertThat(result.roleCode()).isEqualTo("SUPPORT");
        assertThat(target.getAdminRole().getCode()).isEqualTo("SUPPORT");
        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(refreshTokenService).revoke(user);
        verify(userRepository).save(user);
        verify(adminPermissionCache).evictIdentity("staff@genlink.kh");
    }

    @Test
    void deactivationRestoresUserRoleRevokesSessionsAndPreservesTheAdminRowForAudit() {
        UUID adminId = UUID.randomUUID();
        User user = user("staff@genlink.kh", "staff", "Staff User");
        user.setRole(Role.ADMIN);
        AdminUser target = admin(user, "SUPPORT", true);
        when(adminUserRepository.findByIdForUpdate(adminId)).thenReturn(Optional.of(target));
        when(adminUserRepository.save(any(AdminUser.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.deactivate(
            adminId,
            new AdminDeactivationRequest("Revoke staff access after the contract ended"));

        assertThat(result.active()).isFalse();
        assertThat(target.isActive()).isFalse();
        assertThat(target.getDeletedAt()).isEqualTo(NOW);
        assertThat(user.getRole()).isEqualTo(Role.USER);
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(refreshTokenService).revoke(user);
        verify(userRepository).save(user);
        verify(adminPermissionCache).evictIdentity("staff@genlink.kh");
    }

    @Test
    void resetMfaClearsCredentialsRevokesSessionsAndForcesFreshEnrolment() {
        UUID adminId = UUID.randomUUID();
        User user = user("staff@genlink.kh", "staff", "Staff User");
        user.setRole(Role.ADMIN);
        AdminUser target = admin(user, "SUPPORT", true);
        target.setTotpSecret("v1:encrypted-secret");
        target.setRecoveryCodes("old-recovery-codes");
        when(adminUserRepository.findByIdForUpdate(adminId)).thenReturn(Optional.of(target));
        when(adminUserRepository.save(any(AdminUser.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        var result = service.resetMfa(
            adminId,
            new AdminMfaResetRequest("Recover the locked support administrator"));

        assertThat(result.active()).isTrue();
        assertThat(result.totpEnabled()).isFalse();
        assertThat(result.totpConfirmed()).isFalse();
        assertThat(target.getTotpSecret()).isNull();
        assertThat(target.getRecoveryCodes()).isNull();
        assertThat(target.getTotpConfirmedAt()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(superAdminGuard).ensureMayResetMfa(actor, target);
        verify(refreshTokenService).revoke(user);
        verify(userRepository).save(user);
        verify(adminAuditService).record(any(AdminAuditEvent.class));
    }

    @Test
    void resetMfaRefusalDoesNotMutateTheTargetAndIsAudited() {
        UUID adminId = UUID.randomUUID();
        User user = user("super@genlink.kh", "super", "Super Admin");
        user.setRole(Role.ADMIN);
        AdminUser target = admin(user, "SUPER_ADMIN", true);
        target.setTotpSecret("v1:encrypted-secret");
        target.setRecoveryCodes("old-recovery-codes");
        when(adminUserRepository.findByIdForUpdate(adminId)).thenReturn(Optional.of(target));
        doThrow(new ApiException("Only a Super Admin can modify another Super Admin.", "FORBIDDEN"))
            .when(superAdminGuard).ensureMayResetMfa(actor, target);

        assertThatThrownBy(() -> service.resetMfa(
            adminId,
            new AdminMfaResetRequest("Attempt an unauthorised MFA recovery")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "FORBIDDEN");

        assertThat(target.getTotpSecret()).isEqualTo("v1:encrypted-secret");
        assertThat(target.getRecoveryCodes()).isEqualTo("old-recovery-codes");
        verify(refreshTokenService, never()).revoke(user);
        verify(adminUserRepository, never()).save(any(AdminUser.class));
        verify(adminAuditService).recordFailure(
            "ADMIN_MFA_RESET",
            "ADMIN_USER",
            adminId,
            "Attempt an unauthorised MFA recovery",
            "FORBIDDEN");
    }

    @Test
    void failedGuardIsAuditedAndDoesNotMutateTheTarget() {
        UUID adminId = UUID.randomUUID();
        User user = user("super@genlink.kh", "super", "Super Admin");
        user.setRole(Role.ADMIN);
        AdminUser target = admin(user, "SUPER_ADMIN", true);
        when(adminUserRepository.findByIdForUpdate(adminId)).thenReturn(Optional.of(target));
        // The shared guard must reject the update before this service writes.
        org.mockito.Mockito.doThrow(new ApiException(
            "This is the last Super Admin.", "ADMIN_LAST_SUPER_ADMIN"))
            .when(superAdminGuard).ensureMayDeactivate(any(), any());

        assertThatThrownBy(() -> service.deactivate(
            adminId,
            new AdminDeactivationRequest("Attempt to remove the last super admin")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "ADMIN_LAST_SUPER_ADMIN");

        assertThat(target.isActive()).isTrue();
        verify(refreshTokenService, never()).revoke(user);
        verify(adminUserRepository, never()).save(any(AdminUser.class));
        verify(adminAuditService).recordFailure(
            "ADMIN_DEACTIVATE",
            "ADMIN_USER",
            adminId,
            "Attempt to remove the last super admin",
            "ADMIN_LAST_SUPER_ADMIN");
    }

    private static User user(String email, String username, String fullName) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(username);
        user.setFullName(fullName);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setEmailVerified(true);
        return user;
    }

    private static AdminUser admin(String email, String roleCode, boolean active) {
        return admin(user(email, email.substring(0, email.indexOf('@')), email), roleCode, active);
    }

    private static AdminUser admin(User user, String roleCode, boolean active) {
        AdminRole role = role(roleCode, roleCode);
        AdminUser admin = new AdminUser();
        admin.setUser(user);
        admin.setAdminRole(role);
        admin.setActive(active);
        admin.setTotpEnabled(active);
        if (active) {
            admin.setTotpConfirmedAt(NOW);
        }
        return admin;
    }

    private static AdminRole role(String code, String name) {
        AdminRole role = new AdminRole();
        role.setCode(code);
        role.setName(name);
        return role;
    }
}
