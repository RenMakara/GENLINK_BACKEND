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

/** Creates the first {@code SUPER_ADMIN} (SSOT §13.3, ADM-030). */
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
            // The normal state of every environment.
            return;
        }
        bootstrap(email.trim());
    }

    private void bootstrap(String email) {
        // The guard, checked first and before anything is read about the named user.
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
            // Deliberately includes SOFT-DELETED profiles.
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
        // TOTP deliberately unset: totp_confirmed_at stays null.
        admin.setTotpEnabled(false);
        adminUserRepository.save(admin);

        // Keeps the legacy hasRole('ADMIN') routes reachable for this account.
        user.setRole(Role.ADMIN);
        userRepository.save(user);

        writeAuditRow(user, admin);

        // WARN, not INFO: creating a SUPER_ADMIN is the single most privileged event in the system's life.
        log.warn("ADMIN BOOTSTRAP: {} promoted to SUPER_ADMIN", email);
        log.warn("ADMIN BOOTSTRAP: now remove app.admin.bootstrap-email from this environment.");
    }

    /** Reports a refusal without stopping the application. */
    private void failLoudly(String email, String reason) {
        log.error("ADMIN BOOTSTRAP FAILED for {} - {} Nobody was promoted.", email, reason);
    }

    /** The origin of the first admin goes on the record like everything else. */
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
