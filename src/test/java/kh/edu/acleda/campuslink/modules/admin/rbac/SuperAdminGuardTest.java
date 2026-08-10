package kh.edu.acleda.campuslink.modules.admin.rbac;

import java.time.Instant;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminRole;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

/** ADM-031 - SSOT §5.1 D5. The team cannot escalate its own privileges or lock itself out. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class SuperAdminGuardTest {

    @Mock private AdminUserRepository adminUserRepository;

    private SuperAdminGuard guard;
    private AdminUser superAdmin;
    private AdminUser ordinaryAdmin;
    private AdminUser moderator;

    @BeforeEach
    void setUp() {
        guard = new SuperAdminGuard(adminUserRepository);
        superAdmin = admin("owner@genlink.kh", "SUPER_ADMIN");
        ordinaryAdmin = admin("admin@genlink.kh", "ADMIN");
        moderator = admin("mod@genlink.kh", "MODERATOR");

        // Two or more, so the last-SUPER_ADMIN guard is not the thing under test by accident.
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(2L);
    }

    /**
     * Identity comes from the email here: {@code BaseEntity} generates ids, so unsaved entities
     * have none. The guard compares ids when both are present and falls back to email, which is
     * what the security context carries anyway.
     */
    private static AdminUser admin(String email, String roleCode) {
        User user = new User();
        user.setEmail(email);
        AdminRole role = new AdminRole();
        role.setCode(roleCode);
        AdminUser admin = new AdminUser();
        admin.setUser(user);
        admin.setAdminRole(role);
        admin.setActive(true);
        return admin;
    }

    // -------------------------------------------------------------------------------
    // AC#1 - self modification.
    // -------------------------------------------------------------------------------

    @Test
    void anAdminCannotChangeTheirOwnRole() {
        assertThatThrownBy(() -> guard.ensureMayChangeRole(superAdmin, superAdmin, "ADMIN"))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.ADMIN_SELF_MODIFICATION);
    }

    @Test
    void anAdminCannotDeactivateThemselves() {
        assertThatThrownBy(() -> guard.ensureMayDeactivate(ordinaryAdmin, ordinaryAdmin))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.ADMIN_SELF_MODIFICATION);
    }

    /** Even the owner. Self-promotion is the escalation this exists to stop. */
    @Test
    void aSuperAdminCannotPromoteThemselvesEither() {
        AdminUser sameSuperAdmin = admin("owner@genlink.kh", "SUPER_ADMIN");

        assertThatThrownBy(() -> guard.ensureNotSelf(superAdmin, sameSuperAdmin))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.ADMIN_SELF_MODIFICATION);
    }

    @Test
    void identityIsNotCaseSensitive() {
        AdminUser sameByDifferentCasing = admin("OWNER@GENLINK.KH", "SUPER_ADMIN");

        assertThatThrownBy(() -> guard.ensureNotSelf(superAdmin, sameByDifferentCasing))
            .isInstanceOf(ApiException.class);
    }

    @Test
    void modifyingSomeoneElseIsFine() {
        assertThatCode(() -> guard.ensureNotSelf(superAdmin, ordinaryAdmin))
            .doesNotThrowAnyException();
    }

    /**
     * A guard that cannot tell must refuse, not assume. Returning "not the same person" for an
     * entity it could not compare would let the call site believe the check ran.
     */
    @Test
    void refusesRatherThanGuessingWhenIdentityCannotBeEstablished() {
        AdminUser anonymous = new AdminUser();
        anonymous.setAdminRole(ordinaryAdmin.getAdminRole());

        assertThatThrownBy(() -> guard.ensureNotSelf(anonymous, ordinaryAdmin))
            .isInstanceOf(IllegalArgumentException.class)
            .hasMessageContaining("Refusing rather than assuming");
    }

    @Test
    void refusesWhenEitherAdminIsMissing() {
        assertThatThrownBy(() -> guard.ensureNotSelf(null, ordinaryAdmin))
            .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> guard.ensureNotSelf(superAdmin, null))
            .isInstanceOf(IllegalArgumentException.class);
    }

    // -------------------------------------------------------------------------------
    // AC#2 - the last SUPER_ADMIN.
    // -------------------------------------------------------------------------------

    @Test
    void theOnlySuperAdminCannotBeDemoted() {
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(1L);
        AdminUser otherSuperAdmin = admin("owner2@genlink.kh", "SUPER_ADMIN");

        assertThatThrownBy(() -> guard.ensureMayChangeRole(superAdmin, otherSuperAdmin, "ADMIN"))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.ADMIN_LAST_SUPER_ADMIN);
    }

    @Test
    void theOnlySuperAdminCannotBeDeactivated() {
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(1L);
        AdminUser otherSuperAdmin = admin("owner2@genlink.kh", "SUPER_ADMIN");

        assertThatThrownBy(() -> guard.ensureMayDeactivate(superAdmin, otherSuperAdmin))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.ADMIN_LAST_SUPER_ADMIN);
    }

    @Test
    void oneOfSeveralSuperAdminsCanBeDemoted() {
        AdminUser otherSuperAdmin = admin("owner2@genlink.kh", "SUPER_ADMIN");

        assertThatCode(() -> guard.ensureMayChangeRole(superAdmin, otherSuperAdmin, "ADMIN"))
            .doesNotThrowAnyException();
    }

    /** Promoting TO SUPER_ADMIN raises the count, so the guard must not block it. */
    @Test
    void promotingSomeoneToSuperAdminIsNotBlockedByTheLastAdminGuard() {
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(1L);

        assertThatCode(() -> guard.ensureMayChangeRole(superAdmin, ordinaryAdmin, "SUPER_ADMIN"))
            .doesNotThrowAnyException();
    }

    @Test
    void demotingANonSuperAdminNeverConsultsTheCount() {
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(1L);

        assertThatCode(() -> guard.ensureMayChangeRole(superAdmin, moderator, "SUPPORT"))
            .doesNotThrowAnyException();
    }

    /** An already-inactive SUPER_ADMIN is not counted, so removing it cannot lock anyone out. */
    @Test
    void anInactiveSuperAdminIsNotTheLastOne() {
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(1L);
        AdminUser inactive = admin("former@genlink.kh", "SUPER_ADMIN");
        inactive.setActive(false);

        assertThatCode(() -> guard.ensureNotLastSuperAdmin(inactive)).doesNotThrowAnyException();
    }

    @Test
    void aSoftDeletedSuperAdminIsNotTheLastOne() {
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(1L);
        AdminUser deleted = admin("former@genlink.kh", "SUPER_ADMIN");
        deleted.setDeletedAt(Instant.now());

        assertThatCode(() -> guard.ensureNotLastSuperAdmin(deleted)).doesNotThrowAnyException();
    }

    // -------------------------------------------------------------------------------
    // AC#3 - only a SUPER_ADMIN may create or modify another.
    // -------------------------------------------------------------------------------

    @Test
    void anOrdinaryAdminCannotAppointASuperAdmin() {
        assertThatThrownBy(() -> guard.ensureMayChangeRole(ordinaryAdmin, moderator, "SUPER_ADMIN"))
            .isInstanceOf(ForbiddenException.class);
    }

    /**
     * The direction that is easy to miss. Guarding only the appointment would leave the whole
     * SUPER_ADMIN tier removable by the tier below it.
     */
    @Test
    void anOrdinaryAdminCannotDemoteASuperAdmin() {
        assertThatThrownBy(() -> guard.ensureMayChangeRole(ordinaryAdmin, superAdmin, "MODERATOR"))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void anOrdinaryAdminCannotDeactivateASuperAdmin() {
        assertThatThrownBy(() -> guard.ensureMayDeactivate(ordinaryAdmin, superAdmin))
            .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void aSuperAdminMayAppointAnother() {
        assertThatCode(() -> guard.ensureMayChangeRole(superAdmin, ordinaryAdmin, "SUPER_ADMIN"))
            .doesNotThrowAnyException();
    }

    @Test
    void anOrdinaryAdminMayManageNonSuperAdmins() {
        assertThatCode(() -> guard.ensureMayChangeRole(ordinaryAdmin, moderator, "SUPPORT"))
            .doesNotThrowAnyException();
        assertThatCode(() -> guard.ensureMayDeactivate(ordinaryAdmin, moderator))
            .doesNotThrowAnyException();
    }

    /** A 403, not a 400: it is a permission failure, and the portal treats the two differently. */
    @Test
    void theTierRefusalIsAPermissionFailure() {
        assertThatThrownBy(() -> guard.ensureMayManage(moderator, superAdmin, null))
            .isInstanceOf(ForbiddenException.class)
            .hasFieldOrPropertyWithValue("errorCode", "FORBIDDEN");
    }

    // -------------------------------------------------------------------------------
    // Composition - a call site must not be able to apply two of the three.
    // -------------------------------------------------------------------------------

    /** Self is checked first, so it wins even when the tier rule would also refuse. */
    @Test
    void selfIsRejectedBeforeTheTierRule() {
        assertThatThrownBy(() -> guard.ensureMayChangeRole(superAdmin, superAdmin, "ADMIN"))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.ADMIN_SELF_MODIFICATION);
    }

    /** Entitlement before the count: an unentitled actor is refused whatever the population. */
    @Test
    void theTierRuleIsCheckedBeforeTheLastAdminCount() {
        when(adminUserRepository.countByAdminRoleCodeAndActiveTrueAndDeletedAtIsNull("SUPER_ADMIN"))
            .thenReturn(1L);

        assertThatThrownBy(() -> guard.ensureMayDeactivate(ordinaryAdmin, superAdmin))
            .isInstanceOf(ForbiddenException.class);
    }
}
