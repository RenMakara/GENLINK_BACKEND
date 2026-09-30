package kh.edu.acleda.campuslink.modules.admin.rbac;

import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/** The three guards that stop the team escalating its own privileges or locking itself out (SSOT §5.1 D5, ADM-031). */
@Slf4j
@Component
public class SuperAdminGuard {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final AdminUserRepository adminUserRepository;

    public SuperAdminGuard(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    /** Guard 1 — nobody edits their own role or deactivates themselves. */
    public void ensureNotSelf(AdminUser actor, AdminUser target) {
        requireBoth(actor, target);
        if (isSamePerson(actor, target)) {
            log.warn("Admin {} attempted to modify their own role or active state",
                emailOf(actor));
            throw new ApiException(
                "You cannot change your own role or deactivate your own account.",
                AdminErrorCode.ADMIN_SELF_MODIFICATION);
        }
    }

    /** Identity by admin id, falling back to the account's email. */
    private static boolean isSamePerson(AdminUser actor, AdminUser target) {
        if (actor.getId() != null && target.getId() != null) {
            return actor.getId().equals(target.getId());
        }
        String actorEmail = emailOrNull(actor);
        String targetEmail = emailOrNull(target);
        if (actorEmail != null && targetEmail != null) {
            return actorEmail.equalsIgnoreCase(targetEmail);
        }
        throw new IllegalArgumentException(
            "cannot establish whether these are the same admin - no comparable id or email. "
                + "Refusing rather than assuming they differ.");
    }

    private static String emailOrNull(AdminUser admin) {
        return admin.getUser() == null ? null : admin.getUser().getEmail();
    }

    /** Guard 2 — the last active {@code SUPER_ADMIN} cannot be demoted or deactivated. */
    public void ensureNotLastSuperAdmin(AdminUser target) {
        if (target == null) {
            throw new IllegalArgumentException("target is required");
        }
        if (!isSuperAdmin(target) || !target.isUsable()) {
            // Demoting a non-SUPER_ADMIN, or one already inactive, cannot reduce the count.
            return;
        }
        long remaining = adminUserRepository
            .countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull(SUPER_ADMIN);
        if (remaining <= 1) {
            log.warn("Refused to remove the last active SUPER_ADMIN ({})", emailOf(target));
            throw new ApiException(
                "This is the last Super Admin. Appoint another before changing this one.",
                AdminErrorCode.ADMIN_LAST_SUPER_ADMIN);
        }
    }

    /** Guard 3 — only a {@code SUPER_ADMIN} may create or modify another. */
    public void ensureMayManage(AdminUser actor, AdminUser target, String newRoleCode) {
        requireBoth(actor, target);
        if (isSuperAdmin(actor)) {
            return;
        }
        if (SUPER_ADMIN.equals(newRoleCode)) {
            log.warn("Admin {} ({}) attempted to appoint a SUPER_ADMIN",
                emailOf(actor), roleOf(actor));
            throw new ForbiddenException(
                "Only a Super Admin can appoint another Super Admin.", "FORBIDDEN");
        }
        if (isSuperAdmin(target)) {
            log.warn("Admin {} ({}) attempted to modify SUPER_ADMIN {}",
                emailOf(actor), roleOf(actor), emailOf(target));
            throw new ForbiddenException(
                "Only a Super Admin can modify another Super Admin.", "FORBIDDEN");
        }
    }

    /** All three, in the order a role change needs them. */
    public void ensureMayChangeRole(AdminUser actor, AdminUser target, String newRoleCode) {
        ensureNotSelf(actor, target);
        ensureMayManage(actor, target, newRoleCode);
        if (!SUPER_ADMIN.equals(newRoleCode)) {
            // Only a change AWAY from SUPER_ADMIN can reduce the population.
            ensureNotLastSuperAdmin(target);
        }
    }

    /** The same three for deactivation, where no new role is assigned. */
    public void ensureMayDeactivate(AdminUser actor, AdminUser target) {
        ensureNotSelf(actor, target);
        ensureMayManage(actor, target, null);
        ensureNotLastSuperAdmin(target);
    }

    /** Guards an MFA reset for another administrator. */
    public void ensureMayResetMfa(AdminUser actor, AdminUser target) {
        ensureNotSelf(actor, target);
        ensureMayManage(actor, target, null);
    }

    private static boolean isSuperAdmin(AdminUser admin) {
        return admin.getAdminRole() != null && SUPER_ADMIN.equals(admin.getAdminRole().getCode());
    }

    private static void requireBoth(AdminUser actor, AdminUser target) {
        if (actor == null || target == null) {
            // Never a user error: a call site that lost track of who is acting on whom must
            // fail here rather than skip the guard.
            throw new IllegalArgumentException("both the acting and target admin are required");
        }
    }

    private static String roleOf(AdminUser admin) {
        return admin.getAdminRole() == null ? "unknown" : admin.getAdminRole().getCode();
    }

    private static String emailOf(AdminUser admin) {
        return admin.getUser() == null ? "unknown" : admin.getUser().getEmail();
    }
}
