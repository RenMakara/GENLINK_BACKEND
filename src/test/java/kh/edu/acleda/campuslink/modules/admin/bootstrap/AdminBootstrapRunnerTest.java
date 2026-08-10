package kh.edu.acleda.campuslink.modules.admin.bootstrap;

import java.time.Instant;
import java.util.Optional;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditWriter;
import kh.edu.acleda.campuslink.modules.admin.audit.entity.AdminAuditLog;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminRoleRepository;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.Role;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADM-030 - the first SUPER_ADMIN. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminBootstrapRunnerTest {

    private static final String EMAIL = "founder@genlink.kh";

    @Mock private UserRepository userRepository;
    @Mock private AdminUserRepository adminUserRepository;
    @Mock private AdminRoleRepository adminRoleRepository;
    @Mock private AdminAuditWriter auditWriter;

    private AdminBootstrapProperties properties;
    private AdminBootstrapRunner runner;
    private User user;
    private AdminRole superAdminRole;

    @BeforeEach
    void setUp() {
        properties = new AdminBootstrapProperties();
        properties.setBootstrapEmail(EMAIL);
        runner = new AdminBootstrapRunner(properties, userRepository, adminUserRepository,
            adminRoleRepository, auditWriter);

        user = new User();
        user.setEmail(EMAIL);
        user.setEmailVerified(true);
        user.setAccountStatus(AccountStatus.ACTIVE);
        user.setRole(Role.USER);

        superAdminRole = new AdminRole();
        superAdminRole.setCode("SUPER_ADMIN");
        superAdminRole.setName("Super Admin");

        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
        when(adminRoleRepository.findByCode("SUPER_ADMIN")).thenReturn(Optional.of(superAdminRole));
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(0L);
        when(adminUserRepository.existsByUserId(any())).thenReturn(false);
    }

    private void run() {
        runner.run(null);
    }

    private AdminUser savedAdmin() {
        ArgumentCaptor<AdminUser> captor = ArgumentCaptor.forClass(AdminUser.class);
        verify(adminUserRepository).save(captor.capture());
        return captor.getValue();
    }

    // -------------------------------------------------------------------------------
    // AC#1 - with the property set and no admin existing, the user is promoted.
    // -------------------------------------------------------------------------------

    @Test
    void promotesTheNamedUserToSuperAdmin() {
        run();

        AdminUser admin = savedAdmin();
        assertThat(admin.getUser()).isSameAs(user);
        assertThat(admin.getAdminRole().getCode()).isEqualTo("SUPER_ADMIN");
        assertThat(admin.isActive()).isTrue();
        assertThat(admin.getDeletedAt()).isNull();
    }

    @Test
    void setsTheLegacyUserRoleToAdmin() {
        run();

        assertThat(user.getRole()).isEqualTo(Role.ADMIN);
        verify(userRepository).save(user);
    }

    // -------------------------------------------------------------------------------
    // AC#4 - first login forces TOTP enrolment.
    // -------------------------------------------------------------------------------

    /**
     * The promoted account must arrive with no confirmed second factor, so sign-in routes it
     * through enrolment. A bootstrap that pre-set TOTP would mean the first admin's second
     * factor was chosen by whoever ran the deploy.
     */
    @Test
    void leavesTotpUnconfirmedSoTheFirstSignInMustEnrol() {
        run();

        AdminUser admin = savedAdmin();
        assertThat(admin.getTotpConfirmedAt()).isNull();
        assertThat(admin.isTotpEnabled()).isFalse();
        assertThat(admin.getTotpSecret()).isNull();
        assertThat(admin.isEnrolled()).isFalse();
    }

    // -------------------------------------------------------------------------------
    // AC#2 - the guard. The most important behaviour in the task.
    // -------------------------------------------------------------------------------

    /**
     * Without this, anyone able to set an environment variable could promote themselves on the
     * next restart - a permanent privilege-escalation path left behind by a checklist step
     * nobody performed.
     */
    @Test
    void doesNothingWhileAnActiveSuperAdminExists() {
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(1L);

        run();

        verify(adminUserRepository, never()).save(any());
        verify(userRepository, never()).save(any());
        verify(auditWriter, never()).writeInCurrentTransaction(any());
    }

    /**
     * The guard is checked before the named user is read at all, so a running system cannot be
     * probed for which addresses exist by restarting and watching the logs.
     */
    @Test
    void doesNotEvenLookUpTheUserWhenAnAdminAlreadyExists() {
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(1L);

        run();

        verify(userRepository, never()).findByEmail(anyString());
    }

    // -------------------------------------------------------------------------------
    // Not configured
    // -------------------------------------------------------------------------------

    @Test
    void doesNothingWhenThePropertyIsUnset() {
        properties.setBootstrapEmail(null);

        run();

        verify(adminUserRepository, never()).save(any());
        verify(adminUserRepository, never())
            .countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull(anyString());
    }

    @Test
    void doesNothingWhenThePropertyIsBlank() {
        properties.setBootstrapEmail("   ");

        run();

        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void trimsTheConfiguredAddress() {
        properties.setBootstrapEmail("  " + EMAIL + "  ");

        run();

        verify(userRepository).findByEmail(EMAIL);
    }

    // -------------------------------------------------------------------------------
    // AC#3 - refusals promote nobody.
    // -------------------------------------------------------------------------------

    @Test
    void refusesAnAddressWithNoAccount() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        run();

        verify(adminUserRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    @Test
    void refusesAnUnverifiedAccount() {
        user.setEmailVerified(false);

        run();

        verify(adminUserRepository, never()).save(any());
        assertThat(user.getRole()).isEqualTo(Role.USER);
    }

    /**
     * Anything other than ACTIVE is treated as disabled by {@code CustomUserDetailsService}, so
     * promoting one would create an admin who cannot sign in - a bootstrap that appears to have
     * worked and has not.
     */
    @Test
    void refusesAnAccountThatCouldNotSignInAnyway() {
        user.setAccountStatus(AccountStatus.PENDING);
        run();
        verify(adminUserRepository, never()).save(any());

        user.setAccountStatus(AccountStatus.BANNED);
        run();
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void refusesASoftDeletedAccount() {
        user.setDeletedAt(Instant.now());

        run();

        verify(adminUserRepository, never()).save(any());
    }

    /**
     * Reachable when the only admin profile holds a non-SUPER_ADMIN role: the guard passes, but
     * the unique constraint on {@code user_id} would reject the insert. Better a clear log line
     * than a constraint violation at startup.
     */
    @Test
    void refusesAUserWhoAlreadyHasAnAdminProfile() {
        // Matched by any(): BaseEntity generates ids, so an unsaved User has none to stub on.
        when(adminUserRepository.existsByUserId(any())).thenReturn(true);

        run();

        verify(adminUserRepository, never()).save(any());
    }

    /**
     * The check must span SOFT-DELETED profiles too. {@code admin_users.user_id} is UNIQUE
     * unconditionally, so a deactivated profile still holds the slot - and soft delete is the
     * natural way to "clear" an admin in this codebase. Missing this would turn a clear refusal
     * into a constraint violation out of an ApplicationRunner, which stops the app from
     * starting: an admin-provisioning mistake becoming a total outage.
     */
    @Test
    void refusesWhenTheExistingAdminProfileIsOnlySoftDeleted() {
        when(adminUserRepository.existsByUserId(any())).thenReturn(true);
        // Nothing LIVE exists, so the SUPER_ADMIN guard passes and we reach the insert path.
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(0L);

        org.assertj.core.api.Assertions.assertThatCode(this::run).doesNotThrowAnyException();
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void refusesWhenTheSuperAdminRoleIsMissing() {
        when(adminRoleRepository.findByCode("SUPER_ADMIN")).thenReturn(Optional.empty());

        run();

        verify(adminUserRepository, never()).save(any());
        verify(userRepository, never()).save(any());
    }

    /**
     * A refusal must not stop the application. This container also serves the mobile API, so a
     * typo in an admin-provisioning property must not become an outage for every user.
     */
    @Test
    void aRefusalDoesNotThrow() {
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.empty());

        org.assertj.core.api.Assertions.assertThatCode(this::run).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------------
    // AC#5 - the audit row, with a null admin_user_id.
    // -------------------------------------------------------------------------------

    @Test
    void writesAnAuditRowWithNoActingAdmin() {
        run();

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditWriter).writeInCurrentTransaction(captor.capture());
        AdminAuditLog entry = captor.getValue();

        // Null because there was no admin to attribute it to - which is exactly the fact worth
        // recording about the first one (SSOT §13.3).
        assertThat(entry.getAdminUser()).isNull();
        assertThat(entry.getAction()).isEqualTo("ADMIN_BOOTSTRAP");
        assertThat(entry.getTargetType()).isEqualTo("ADMIN_USER");
        assertThat(entry.getReason()).contains(EMAIL).contains("SUPER_ADMIN");
    }

    @Test
    void theAuditRowRecordsSuccess() {
        run();

        ArgumentCaptor<AdminAuditLog> captor = ArgumentCaptor.forClass(AdminAuditLog.class);
        verify(auditWriter).writeInCurrentTransaction(captor.capture());

        assertThat(captor.getValue().getStatus())
            .isEqualTo(kh.edu.acleda.campuslink.modules.admin.audit.enums.AuditStatus.SUCCESS);
    }

    /** Running twice must not produce a second admin - the guard, from the caller's side. */
    @Test
    void isIdempotentAcrossRestarts() {
        run();
        verify(adminUserRepository).save(any());

        // Second boot: the admin created above now exists.
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(1L);
        run();

        verify(adminUserRepository).save(any());
    }
}
