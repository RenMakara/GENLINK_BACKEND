package kh.edu.acleda.campuslink.modules.admin.bootstrap;

import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditWriter;
import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminRoleRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.Role;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates the first {@code SUPER_ADMIN} (SSOT §13.3, ADM-030).
 *
 * <h2>The chicken-and-egg it resolves</h2>
 *
 * {@code POST /admin/admins} needs {@code rbac:manage}, and TOTP enrolment needs an
 * {@code admin_users} row. With no admin, nobody can create one, and the portal cannot be
 * signed into at all. Migration 0041 deliberately seeds no admin - a seeded admin means a known
 * account with a known address in every deployment.
 *
 * <h2>The line that matters</h2>
 *
 * <strong>It does nothing while any active {@code SUPER_ADMIN} exists.</strong> That guard is
 * what stops this being a permanent privilege-escalation path when the property is left set in
 * production, which it will be, because removing it is a checklist step and checklist steps get
 * skipped. Without it, anyone able to set an environment variable could promote themselves on
 * the next restart.
 *
 * <p>Deliberately awkward: an operator action performed once per environment, not a convenient
 * back door.
 */
@Slf4j
@Component
public class AdminBootstrapRunner implements ApplicationRunner {

    static final String SUPER_ADMIN = "SUPER_ADMIN";
    static final String ACTION = "ADMIN_BOOTSTRAP";

    private final AdminBootstrapProperties properties;
    private final UserRepository userRepository;
    private final AdminUserRepository adminUserRepository;
    private final AdminRoleRepository adminRoleRepository;
    private final AdminAuditWriter auditWriter;

    public AdminBootstrapRunner(AdminBootstrapProperties properties,
                                UserRepository userRepository,
                                AdminUserRepository adminUserRepository,
                                AdminRoleRepository adminRoleRepository,
                                AdminAuditWriter auditWriter) {
        this.properties = properties;
        this.userRepository = userRepository;
        this.adminUserRepository = adminUserRepository;
        this.adminRoleRepository = adminRoleRepository;
        this.auditWriter = auditWriter;
    }

    @Override
    @Transactional
    public void run(ApplicationArguments args) {
        String email = properties.getBootstrapEmail();
        if (email == null || email.isBlank()) {
            // The normal state of every environment. Silent on purpose - a log line on every
            // boot for a property nobody set is noise that trains people to skim.
            return;
        }
        bootstrap(email.trim());
    }

    private void bootstrap(String email) {
        // The guard, checked first and before anything is read about the named user, so that a
        // running system cannot be probed for which addresses exist by watching the logs.
        long existing = adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull(SUPER_ADMIN);
        if (existing > 0) {
            log.info("ADMIN BOOTSTRAP: skipped - {} active SUPER_ADMIN already exists. "
                + "Remove app.admin.bootstrap-email from this environment.", existing);
            return;
        }

        User user = userRepository.findByEmail(email).orElse(null);
        if (user == null) {
            failLoudly(email, "no users row with that address. The bootstrap promotes an "
                + "EXISTING account - register through the app first.");
            return;
        }
        if (!user.isEmailVerified()) {
            failLoudly(email, "that account has not verified its email. Complete the OTP "
                + "round trip, then restart.");
            return;
        }
        if (user.getAccountStatus() != AccountStatus.ACTIVE || user.getDeletedAt() != null) {
            // Would create an admin who cannot authenticate: CustomUserDetailsService treats
            // anything other than ACTIVE as disabled.
            failLoudly(email, "that account is " + user.getAccountStatus()
                + ", so it could not sign in even once promoted.");
            return;
        }
        if (adminUserRepository.existsByUserId(user.getId())) {
            // Deliberately includes SOFT-DELETED profiles. admin_users.user_id is UNIQUE
            // unconditionally, so a deactivated profile still holds the slot - and the most
            // natural way to "clear" an admin in this codebase is a soft delete. Checking only
            // for a live profile would let the insert below hit the constraint, and an
            // exception out of an ApplicationRunner stops the application from starting.
            //
            // Reachable two ways: a non-SUPER_ADMIN profile (the guard above passed), or a
            // soft-deleted one.
            failLoudly(email, "that account already has an admin profile, possibly a "
                + "soft-deleted one - admin_users.user_id is unique either way. Restore it, or "
                + "hard-delete the row, rather than bootstrapping over it.");
            return;
        }

        AdminRole role = adminRoleRepository.findByCode(SUPER_ADMIN).orElse(null);
        if (role == null) {
            failLoudly(email, "the SUPER_ADMIN role is missing. Migration 0041 seeds it - "
                + "check that migrations ran.");
            return;
        }

        AdminUser admin = new AdminUser();
        admin.setUser(user);
        admin.setAdminRole(role);
        admin.setActive(true);
        // TOTP deliberately unset: totp_confirmed_at stays null, so the first sign-in is forced
        // through enrolment (SSOT §10.8) and this account never exists with a second factor
        // somebody else chose.
        admin.setTotpEnabled(false);
        adminUserRepository.save(admin);

        // Keeps the legacy hasRole('ADMIN') routes reachable for this account. Permissions come
        // from the admin role; this is the mobile-era role model, kept in step (SSOT §5.1).
        user.setRole(Role.ADMIN);
        userRepository.save(user);

        writeAuditRow(user, admin);

        // WARN, not INFO: creating a SUPER_ADMIN is the single most privileged event in the
        // system's life and should stand out in a boot log nobody reads closely.
        log.warn("ADMIN BOOTSTRAP: {} promoted to SUPER_ADMIN", email);
        log.warn("ADMIN BOOTSTRAP: now remove app.admin.bootstrap-email from this environment.");
    }

    /**
     * Reports a refusal without stopping the application.
     *
     * <p>Throwing here would be louder, and was the alternative considered. It is rejected
     * because this container also serves the mobile API: a typo in an admin-provisioning
     * property would take the whole product down for every user. An unmissable ERROR banner
     * satisfies "fails loudly" without letting an admin mistake become an outage.
     */
    private void failLoudly(String email, String reason) {
        log.error("ADMIN BOOTSTRAP FAILED for {} - {} Nobody was promoted.", email, reason);
    }

    /**
     * The origin of the first admin goes on the record like everything else, with
     * {@code admin_user_id = NULL} - there was no admin to attribute it to (SSOT §13.3).
     *
     * <p>Written through {@link AdminAuditWriter} rather than {@code AdminAuditService} because
     * that service resolves the acting admin from the security context, and at startup there is
     * no request, no context and no admin.
     */
    private void writeAuditRow(User user, AdminUser admin) {
        AdminAuditLog entry = new AdminAuditLog();
        entry.setAdminUser(null);
        entry.setAction(ACTION);
        entry.setTargetType("ADMIN_USER");
        entry.setTargetId(admin.getId());
        entry.setReason("Bootstrap promotion of " + user.getEmail() + " to SUPER_ADMIN "
            + "via app.admin.bootstrap-email");
        entry.setStatus(AuditStatus.SUCCESS);
        auditWriter.writeInCurrentTransaction(entry);
    }
}
