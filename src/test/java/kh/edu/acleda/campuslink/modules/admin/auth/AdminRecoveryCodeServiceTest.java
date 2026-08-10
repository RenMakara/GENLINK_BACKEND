package kh.edu.acleda.campuslink.modules.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.RecoveryCodesResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.RecoveryCodeService;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADM-026 - recovery-code regeneration. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminRecoveryCodeServiceTest {

    private static final String EMAIL = "admin@genlink.kh";

    @Mock private AdminUserRepository adminUserRepository;
    @Mock private SecurityAuditService securityAuditService;

    private RecoveryCodeService recoveryCodeService;
    private AdminRecoveryCodeService service;
    private AdminUser admin;

    @BeforeEach
    void setUp() {
        recoveryCodeService = new RecoveryCodeService(new BCryptPasswordEncoder(4), new ObjectMapper());
        service = new AdminRecoveryCodeService(adminUserRepository, recoveryCodeService, securityAuditService);

        User user = new User();
        user.setEmail(EMAIL);
        admin = new AdminUser();
        admin.setUser(user);
        admin.setActive(true);
        admin.setTotpEnabled(true);
        admin.setTotpConfirmedAt(Instant.now());

        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.of(admin));
        authenticate();
    }

    @AfterEach
    void clearContext() {
        SecurityContextHolder.clearContext();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(EMAIL, null,
                List.of(new SimpleGrantedAuthority("PERM_user:read"))));
    }

    /** Gives the admin a known starting set, as enrolment would have. */
    private List<String> givenExistingCodes() {
        RecoveryCodeService.GeneratedCodes original = recoveryCodeService.generate();
        admin.setRecoveryCodes(original.json());
        return original.plaintext();
    }

    // -------------------------------------------------------------------------------
    // AC#1 - the old set is fully invalid afterwards.
    // -------------------------------------------------------------------------------

    /**
     * The reason regeneration exists is distrust of the old set. Every previous code must stop
     * working - including the ones never spent, which are exactly the ones still on paper.
     */
    @Test
    void everyOldCodeFailsAfterRegeneration() {
        List<String> oldCodes = givenExistingCodes();

        service.regenerate();

        for (String old : oldCodes) {
            assertThat(recoveryCodeService.consume(admin.getRecoveryCodes(), old))
                .as("old code %s must no longer work", old)
                .isEmpty();
        }
    }

    /** Overwritten, not merged: a merge would leave the distrusted codes in circulation. */
    @Test
    void theStoredSetIsReplacedNotAppendedTo() {
        givenExistingCodes();

        service.regenerate();

        assertThat(recoveryCodeService.remaining(admin.getRecoveryCodes())).isEqualTo(10);
    }

    @Test
    void issuesTenNewCodes() {
        givenExistingCodes();

        RecoveryCodesResponse response = service.regenerate();

        assertThat(response.recoveryCodes()).hasSize(10).doesNotHaveDuplicates();
    }

    @Test
    void theNewCodesShareNothingWithTheOldOnes() {
        List<String> oldCodes = givenExistingCodes();

        assertThat(service.regenerate().recoveryCodes()).doesNotContainAnyElementsOf(oldCodes);
    }

    @Test
    void theNewCodesWork() {
        givenExistingCodes();

        RecoveryCodesResponse response = service.regenerate();

        assertThat(recoveryCodeService.consume(admin.getRecoveryCodes(), response.recoveryCodes().get(0)))
            .isPresent();
    }

    @Test
    void storesThemHashedNotInPlaintext() {
        givenExistingCodes();

        RecoveryCodesResponse response = service.regenerate();

        for (String code : response.recoveryCodes()) {
            assertThat(admin.getRecoveryCodes()).doesNotContain(code);
        }
    }

    /** An admin who spent every code is the main reason this endpoint exists. */
    @Test
    void worksWhenEveryPreviousCodeWasAlreadySpent() {
        List<String> oldCodes = givenExistingCodes();
        String json = admin.getRecoveryCodes();
        for (String code : oldCodes) {
            json = recoveryCodeService.consume(json, code).orElseThrow();
        }
        admin.setRecoveryCodes(json);
        assertThat(recoveryCodeService.remaining(json)).isZero();

        service.regenerate();

        assertThat(recoveryCodeService.remaining(admin.getRecoveryCodes())).isEqualTo(10);
    }

    @Test
    void persistsTheNewSet() {
        givenExistingCodes();

        service.regenerate();

        verify(adminUserRepository).save(admin);
    }

    // -------------------------------------------------------------------------------
    // AC#2 - requires sudo, and is audited.
    // -------------------------------------------------------------------------------

    /**
     * The sudo window is enforced by the interceptor, which is tested separately - what can
     * still go wrong is the annotation simply not being on the method. A fresh set of codes is
     * a permanent bypass of the second factor, so a stolen session alone must not mint one.
     */
    @Test
    void theEndpointIsAnnotatedRequiresSudo() throws Exception {
        assertThat(AdminAuthController.class
            .getMethod("regenerateRecoveryCodes")
            .isAnnotationPresent(RequiresSudo.class))
            .as("POST /admin/auth/mfa/recovery-codes must carry @RequiresSudo")
            .isTrue();
    }

    @Test
    void regenerationIsAudited() {
        givenExistingCodes();

        service.regenerate();

        verify(securityAuditService)
            .logSuccess(eq("ADMIN_RECOVERY_CODES_REGENERATED"), any(User.class), eq(EMAIL));
    }

    // -------------------------------------------------------------------------------
    // Refusals
    // -------------------------------------------------------------------------------

    /** Codes only mean anything alongside a confirmed second factor. */
    @Test
    void refusesAnAdminWhoHasNotEnrolled() {
        admin.setTotpConfirmedAt(null);

        assertThatThrownBy(() -> service.regenerate())
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.ADMIN_MFA_NOT_ENROLLED);

        assertThat(admin.getRecoveryCodes()).isNull();
        verify(securityAuditService).logFailure(anyString(), any(User.class), eq(EMAIL), eq("NOT_ENROLLED"));
    }

    @Test
    void refusesAnAccountThatIsNoLongerAnAdmin() {
        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.regenerate()).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void refusesAnUnauthenticatedCaller() {
        SecurityContextHolder.clearContext();

        assertThatThrownBy(() -> service.regenerate()).isInstanceOf(UnauthorizedException.class);
    }
}
