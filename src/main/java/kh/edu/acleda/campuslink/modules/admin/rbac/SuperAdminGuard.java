package kh.edu.acleda.campuslink.modules.admin.rbac;

import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

/**
 * The three guards that stop the team escalating its own privileges or locking itself out
 * (SSOT §5.1 D5, ADM-031).
 *
 * <h2>In the service layer, never the controller</h2>
 *
 * Required by the definition of done, and for a reason worth stating: these must hold for
 * <em>every</em> path that changes an admin's role or active state, including ones that do not
 * come from an HTTP request - a future bulk import, an admin CLI, a data migration. A guard in
 * a controller protects one door in a building that will grow more doors.
 *
 * <h2>Why {@code SUPER_ADMIN} is special-cased in code, not data</h2>
 *
 * It implicitly holds every permission and is the only role that can grant
 * {@code rbac:manage}. Expressing "only a SUPER_ADMIN may appoint a SUPER_ADMIN" as a
 * permission row would make it editable by anyone holding {@code rbac:manage} - which is the
 * loop this closes (SSOT §5.1 D5).
 *
 * <p>The endpoints that call these arrive in Phase 5 (`POST /admin/admins`,
 * `PUT /admin/admins/{id}/role`, `POST /admin/admins/{id}/deactivate`). The guards are built
 * first so the endpoints are written against them rather than around them.
 */
@Slf4j
@Component
public class SuperAdminGuard {

    public static final String SUPER_ADMIN = "SUPER_ADMIN";

    private final AdminUserRepository adminUserRepository;

    public SuperAdminGuard(AdminUserRepository adminUserRepository) {
        this.adminUserRepository = adminUserRepository;
    }

    /**
     * Guard 1 — nobody edits their own role or deactivates themselves.
     *
     * <p>Not about protecting an admin from a mistake. It is what stops a compromised session
     * from quietly promoting itself, and it puts a second person's name against every privilege
     * increase in the audit trail.
     *
     * @throws ApiException {@code ADMIN_SELF_MODIFICATION} (400)
     */
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

    /**
     * Identity by admin id, falling back to the account's email.
     *
     * <p>Both are unique per person - {@code admin_users.user_id} is UNIQUE, so one user has at
     * most one profile - and the email is what the security context carries, so it is available
     * on every path the id is.
     *
     * <p><strong>Throws rather than returning false when neither can be compared.</strong> An
     * earlier version read {@code actor.getId() != null && actor.getId().equals(...)}, which
     * silently answered "not the same person" for an entity with no id yet. A guard that fails
     * open is worse than no guard, because the call site believes it ran.
     */
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

    /**
     * Guard 2 — the last active {@code SUPER_ADMIN} cannot be demoted or deactivated.
     *
     * <p>With none left, nobody can appoint one from inside the product: {@code SUPER_ADMIN}
     * is the only role that can grant {@code rbac:manage}. The only way back is the bootstrap
     * runner - which needs a redeploy with {@code app.admin.bootstrap-email} set, by someone
     * with environment access. Recovery is a deployment, not a support ticket.
     *
     * <p>Counts <em>active, non-deleted</em> holders, which is the same population the
     * bootstrap guard uses - a deactivated {@code SUPER_ADMIN} is not a way back in.
     *
     * @throws ApiException {@code ADMIN_LAST_SUPER_ADMIN} (400)
     */
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

    /**
     * Guard 3 — only a {@code SUPER_ADMIN} may create or modify another.
     *
     * <p>Both directions matter, and the second is the one that is easy to miss: an
     * {@code ADMIN} holding {@code rbac:manage} must not be able to <em>demote</em> a
     * {@code SUPER_ADMIN} either. Guarding only the assignment would leave the whole tier
     * removable by the tier below it.
     *
     * @param newRoleCode the role being assigned, or null when only the target matters
     *     (deactivation, MFA reset).
     * @throws ForbiddenException (403) — a permission failure, not a validation one, so it is
     *     reported the way every other missing-permission answer is (SSOT §8 C-1).
     */
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

    /**
     * All three, in the order a role change needs them.
     *
     * <p>Offered so a call site cannot apply two of the three and look complete. Self first
     * because it is the cheapest and needs no query; the last-SUPER_ADMIN count runs only once
     * the actor is known to be entitled to act at all.
     */
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
