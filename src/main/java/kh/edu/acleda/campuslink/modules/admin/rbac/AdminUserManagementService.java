package kh.edu.acleda.campuslink.modules.admin.rbac;

import java.time.Clock;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminDeactivationRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminMfaResetRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminPromotionRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.request.AdminRoleChangeRequest;
import kh.edu.acleda.campuslink.modules.admin.rbac.dto.response.AdminStaffResponse;
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
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import kh.edu.acleda.campuslink.shared.response.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Promotion, role assignment, MFA recovery and revocation of staff accounts (ADM-103/104). */
@Service
public class AdminUserManagementService {

    private static final Set<Integer> ALLOWED_PAGE_SIZES = Set.of(25, 50, 100);
    private static final String TARGET_TYPE_ADMIN = "ADMIN_USER";
    private static final String ACTION_PROMOTE = "ADMIN_PROMOTE";
    private static final String ACTION_ROLE_CHANGE = "ADMIN_ROLE_CHANGE";
    private static final String ACTION_DEACTIVATE = "ADMIN_DEACTIVATE";
    private static final String ACTION_RESET_MFA = "ADMIN_MFA_RESET";
    private static final String VALIDATION_ERROR = "VALIDATION_ERROR";
    private static final String NOT_FOUND = "NOT_FOUND";
    private static final String INTERNAL_SERVER_ERROR = "INTERNAL_SERVER_ERROR";

    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final UserRepository userRepository;
    private final RefreshTokenService refreshTokenService;
    private final AdminPermissionCache adminPermissionCache;
    private final SuperAdminGuard superAdminGuard;
    private final AdminAuditService adminAuditService;
    private final Clock clock;

    @Autowired
    public AdminUserManagementService(
        AdminUserRepository adminUserRepository,
        AdminRoleRepository adminRoleRepository,
        UserRepository userRepository,
        RefreshTokenService refreshTokenService,
        AdminPermissionCache adminPermissionCache,
        SuperAdminGuard superAdminGuard,
        AdminAuditService adminAuditService) {
        this(
            adminUserRepository,
            adminRoleRepository,
            userRepository,
            refreshTokenService,
            adminPermissionCache,
            superAdminGuard,
            adminAuditService,
            Clock.systemUTC());
    }

    AdminUserManagementService(
        AdminUserRepository adminUserRepository,
        AdminRoleRepository adminRoleRepository,
        UserRepository userRepository,
        RefreshTokenService refreshTokenService,
        AdminPermissionCache adminPermissionCache,
        SuperAdminGuard superAdminGuard,
        AdminAuditService adminAuditService,
        Clock clock) {
        this.adminUserRepository = adminUserRepository;
        this.adminRoleRepository = adminRoleRepository;
        this.userRepository = userRepository;
        this.refreshTokenService = refreshTokenService;
        this.adminPermissionCache = adminPermissionCache;
        this.superAdminGuard = superAdminGuard;
        this.adminAuditService = adminAuditService;
        this.clock = clock;
    }

    /** Lists all staff profiles, including revoked profiles, without returning credentials. */
    @Transactional(readOnly = true)
    public PageResponse<AdminStaffResponse> list(int page, int size) {
        Pageable pageable = pageRequest(page, size);
        Page<AdminUser> admins = adminUserRepository.findAll(pageable);
        return PageResponse.from(admins, AdminUserManagementService::toResponse);
    }

    /** Promotes one existing, email-verified, active user into a staff profile. */
    @Transactional
    public AdminStaffResponse promote(AdminPromotionRequest request) {
        String reason = requireReason(request == null ? null : request.reason());
        UUID targetUserId = request == null ? null : request.userId();
        try {
            AdminUser actor = currentAdmin();
            User user = lockedUser(targetUserId);
            AdminRole role = role(request == null ? null : request.roleId());
            AdminUser candidate = candidate(user, role);

            // Run the shared guard before the duplicate check.
            superAdminGuard.ensureMayChangeRole(actor, candidate, role.getCode());
            ensurePromotable(user);
            if (adminUserRepository.existsByUserId(targetUserId)) {
                throw new ApiException(
                    "This user already has an admin profile.", VALIDATION_ERROR);
            }

            candidate.setInvitedBy(actor);
            AdminUser saved = adminUserRepository.save(candidate);
            user.setRole(Role.ADMIN);
            userRepository.save(user);
            adminPermissionCache.evictIdentity(user.getEmail());

            recordSuccess(
                ACTION_PROMOTE,
                saved.getId(),
                reason,
                null,
                new AdminSnapshot(saved));
            return toResponse(saved);
        } catch (NotFoundException ex) {
            recordFailure(ACTION_PROMOTE, targetUserId, reason, NOT_FOUND);
            throw ex;
        } catch (ApiException ex) {
            recordFailure(ACTION_PROMOTE, targetUserId, reason, ex.getErrorCode());
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_PROMOTE, targetUserId, reason, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    /** Changes an existing admin's role and invalidates every token issued to that account. */
    @Transactional
    public AdminStaffResponse changeRole(UUID adminId, AdminRoleChangeRequest request) {
        String reason = requireReason(request == null ? null : request.reason());
        try {
            AdminUser actor = currentAdmin();
            AdminUser target = lockedAdmin(adminId);
            AdminRole newRole = role(request == null ? null : request.roleId());
            superAdminGuard.ensureMayChangeRole(actor, target, newRole.getCode());
            ensureUsable(target);

            String previousCode = target.getAdminRole().getCode();
            if (previousCode.equals(newRole.getCode())) {
                recordSuccess(
                    ACTION_ROLE_CHANGE,
                    adminId,
                    reason,
                    new AdminSnapshot(target),
                    new AdminSnapshot(target));
                return toResponse(target);
            }

            AdminSnapshot before = new AdminSnapshot(target);
            target.setAdminRole(newRole);
            User user = target.getUser();
            user.setRole(Role.ADMIN);
            revokeAllSessions(user);
            adminUserRepository.save(target);
            userRepository.save(user);
            adminPermissionCache.evictIdentity(user.getEmail());

            recordSuccess(ACTION_ROLE_CHANGE, adminId, reason, before, new AdminSnapshot(target));
            return toResponse(target);
        } catch (NotFoundException ex) {
            recordFailure(ACTION_ROLE_CHANGE, adminId, reason, NOT_FOUND);
            throw ex;
        } catch (ApiException ex) {
            recordFailure(ACTION_ROLE_CHANGE, adminId, reason, ex.getErrorCode());
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_ROLE_CHANGE, adminId, reason, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    /** Revokes staff access, keeps the user row, and invalidates all existing sessions. */
    @Transactional
    public AdminStaffResponse deactivate(UUID adminId, AdminDeactivationRequest request) {
        String reason = requireReason(request == null ? null : request.reason());
        try {
            AdminUser actor = currentAdmin();
            AdminUser target = lockedAdmin(adminId);
            superAdminGuard.ensureMayDeactivate(actor, target);

            if (!target.isUsable()) {
                recordSuccess(
                    ACTION_DEACTIVATE,
                    adminId,
                    reason,
                    new AdminSnapshot(target),
                    new AdminSnapshot(target));
                return toResponse(target);
            }

            AdminSnapshot before = new AdminSnapshot(target);
            User user = target.getUser();
            target.setActive(false);
            target.setDeletedAt(clock.instant());
            user.setRole(Role.USER);
            revokeAllSessions(user);
            adminUserRepository.save(target);
            userRepository.save(user);
            adminPermissionCache.evictIdentity(user.getEmail());

            recordSuccess(ACTION_DEACTIVATE, adminId, reason, before, new AdminSnapshot(target));
            return toResponse(target);
        } catch (NotFoundException ex) {
            recordFailure(ACTION_DEACTIVATE, adminId, reason, NOT_FOUND);
            throw ex;
        } catch (ApiException ex) {
            recordFailure(ACTION_DEACTIVATE, adminId, reason, ex.getErrorCode());
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_DEACTIVATE, adminId, reason, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    /** Clears another admin's MFA credentials and forces a fresh enrolment at next sign-in. */
    @Transactional
    public AdminStaffResponse resetMfa(UUID adminId, AdminMfaResetRequest request) {
        String reason = requireReason(request == null ? null : request.reason());
        try {
            AdminUser actor = currentAdmin();
            AdminUser target = lockedAdmin(adminId);
            superAdminGuard.ensureMayResetMfa(actor, target);
            ensureUsable(target);

            AdminMfaSnapshot before = new AdminMfaSnapshot(target);
            User user = target.getUser();
            target.setTotpSecret(null);
            target.setTotpEnabled(false);
            target.setTotpConfirmedAt(null);
            target.setRecoveryCodes(null);
            revokeAllSessions(user);
            adminUserRepository.save(target);
            userRepository.save(user);

            recordSuccess(ACTION_RESET_MFA, adminId, reason, before, new AdminMfaSnapshot(target));
            return toResponse(target);
        } catch (NotFoundException ex) {
            recordFailure(ACTION_RESET_MFA, adminId, reason, NOT_FOUND);
            throw ex;
        } catch (ApiException ex) {
            recordFailure(ACTION_RESET_MFA, adminId, reason, ex.getErrorCode());
            throw ex;
        } catch (RuntimeException ex) {
            recordFailure(ACTION_RESET_MFA, adminId, reason, INTERNAL_SERVER_ERROR);
            throw ex;
        }
    }

    private AdminUser currentAdmin() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()
            || authentication.getName() == null || authentication.getName().isBlank()) {
            throw new UnauthorizedException("Your session is no longer valid.");
        }
        return adminUserRepository.findActiveByUserEmail(authentication.getName())
            .orElseThrow(() -> new UnauthorizedException("Your session is no longer valid."));
    }

    private User lockedUser(UUID userId) {
        if (userId == null) {
            throw new ApiException("userId is required", VALIDATION_ERROR);
        }
        return userRepository.findByIdForUpdate(userId)
            .orElseThrow(() -> new NotFoundException("User not found with id: " + userId));
    }

    private AdminUser lockedAdmin(UUID adminId) {
        if (adminId == null) {
            throw new ApiException("adminId is required", VALIDATION_ERROR);
        }
        return adminUserRepository.findByIdForUpdate(adminId)
            .orElseThrow(() -> new NotFoundException("Admin user not found with id: " + adminId));
    }

    private AdminRole role(UUID roleId) {
        if (roleId == null) {
            throw new ApiException("roleId is required", VALIDATION_ERROR);
        }
        return adminRoleRepository.findById(roleId)
            .orElseThrow(() -> new NotFoundException("Admin role not found with id: " + roleId));
    }

    private static AdminUser candidate(User user, AdminRole role) {
        AdminUser candidate = new AdminUser();
        candidate.setUser(user);
        candidate.setAdminRole(role);
        candidate.setActive(true);
        return candidate;
    }

    private static void ensurePromotable(User user) {
        if (user.getDeletedAt() != null || user.getAccountStatus() != AccountStatus.ACTIVE
            || !user.isEmailVerified()) {
            throw new ApiException(
                "Only an existing, active, email-verified user can become an admin.",
                VALIDATION_ERROR);
        }
    }

    private static void ensureUsable(AdminUser admin) {
        if (!admin.isUsable()) {
            throw new ApiException("The admin profile is not active.", VALIDATION_ERROR);
        }
    }

    private void revokeAllSessions(User user) {
        refreshTokenService.revoke(user);
        user.incrementTokenVersion();
    }

    private void recordSuccess(
        String action, UUID targetId, String reason, Object before, Object after) {
        adminAuditService.record(AdminAuditEvent.builder()
            .action(action)
            .targetType(TARGET_TYPE_ADMIN)
            .targetId(targetId)
            .reason(reason)
            .beforeJson(adminAuditService.toJson(before))
            .afterJson(adminAuditService.toJson(after))
            .build());
    }

    private void recordFailure(String action, UUID targetId, String reason, String errorCode) {
        adminAuditService.recordFailure(action, TARGET_TYPE_ADMIN, targetId, reason, errorCode);
    }

    private static Pageable pageRequest(int page, int size) {
        if (page < 0) {
            throw new ApiException("page must be zero or greater", VALIDATION_ERROR);
        }
        if (!ALLOWED_PAGE_SIZES.contains(size)) {
            throw new ApiException("size must be one of 25, 50, or 100", VALIDATION_ERROR);
        }
        return PageRequest.of(page, size, Sort.by(Sort.Direction.DESC, "createdAt"));
    }

    private static String requireReason(String reason) {
        String normalized = reason == null ? null : reason.trim();
        if (normalized == null || normalized.length() < 10 || normalized.length() > 2000) {
            throw new ApiException("reason: size must be between 10 and 2000", VALIDATION_ERROR);
        }
        return normalized;
    }

    private static AdminStaffResponse toResponse(AdminUser admin) {
        AdminRole role = admin.getAdminRole();
        User user = admin.getUser();
        return new AdminStaffResponse(
            admin.getId(),
            user.getId(),
            user.getFullName(),
            user.getUsername(),
            user.getEmail(),
            role == null ? null : role.getId(),
            role == null ? null : role.getCode(),
            role == null ? null : role.getName(),
            admin.isUsable(),
            admin.isTotpEnabled(),
            admin.isEnrolled(),
            admin.getLastLoginAt(),
            admin.getCreatedAt(),
            admin.getDeletedAt());
    }

    private record AdminSnapshot(
        UUID adminId,
        UUID userId,
        String roleCode,
        boolean active,
        Instant deletedAt,
        int tokenVersion) {

        private AdminSnapshot(AdminUser admin) {
            this(
                admin.getId(),
                admin.getUser() == null ? null : admin.getUser().getId(),
                admin.getAdminRole() == null ? null : admin.getAdminRole().getCode(),
                admin.isActive(),
                admin.getDeletedAt(),
                admin.getUser() == null ? 0 : admin.getUser().getTokenVersion());
        }
    }

    /** Audit-safe MFA state; never serialise the encrypted secret or recovery-code hashes. */
    private record AdminMfaSnapshot(
        UUID adminId,
        UUID userId,
        String roleCode,
        boolean active,
        boolean totpEnabled,
        boolean enrolled,
        boolean recoveryCodesPresent,
        int tokenVersion) {

        private AdminMfaSnapshot(AdminUser admin) {
            this(
                admin.getId(),
                admin.getUser() == null ? null : admin.getUser().getId(),
                admin.getAdminRole() == null ? null : admin.getAdminRole().getCode(),
                admin.isUsable(),
                admin.isTotpEnabled(),
                admin.isEnrolled(),
                admin.getRecoveryCodes() != null && !admin.getRecoveryCodes().isBlank(),
                admin.getUser() == null ? 0 : admin.getUser().getTokenVersion());
        }
    }
}
