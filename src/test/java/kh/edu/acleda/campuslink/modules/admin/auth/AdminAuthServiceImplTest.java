package kh.edu.acleda.campuslink.modules.admin.auth;

import java.util.Optional;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.AdminLoginRequest;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminMfaChallengeResponse;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.modules.auth.security.AuthRateLimitService;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.RateLimitedException;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import kh.edu.acleda.campuslink.shared.security.JwtProperties;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADM-021. The endpoint's whole job is to be uninformative, so most of this file is about what
 * an attacker can <em>not</em> learn from it.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminAuthServiceImplTest {

    private static final String EMAIL = "admin@genlink.kh";
    private static final String PASSWORD = "correct-horse-battery-staple";
    private static final String CHALLENGE_TOKEN = "challenge.jwt.token";

    @Mock
    private AuthenticationManager authenticationManager;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminUserRepository adminUserRepository;
    @Mock
    private AuthRateLimitService authRateLimitService;
    @Mock
    private SecurityAuditService securityAuditService;
    @Mock
    private JwtService jwtService;

    private AdminAuthServiceImpl service;

    /**
     * A real BCrypt encoder, not a mock. The timing decoy is only meaningful if an actual hash
     * comparison happens, and a mocked encoder would return instantly and prove nothing.
     * Strength 4 keeps the suite fast; the cost factor is irrelevant to what is asserted.
     */
    private PasswordEncoder passwordEncoder;

    @BeforeEach
    void setUp() {
        passwordEncoder = new BCryptPasswordEncoder(4);
        JwtProperties properties = new JwtProperties();
        service = new AdminAuthServiceImpl(authenticationManager, userRepository, adminUserRepository,
            authRateLimitService, securityAuditService, jwtService, properties, passwordEncoder);

        when(jwtService.generateAdminMfaChallengeToken(anyString(), anyString())).thenReturn(CHALLENGE_TOKEN);
    }

    // -----------------------------------------------------------------------------------
    // AC#1 - valid admin credentials yield a challenge, never a session.
    // -----------------------------------------------------------------------------------

    @Test
    void issuesChallengeTokenForValidAdminCredentials() {
        givenUserExists();
        givenPasswordIsCorrect();
        givenActiveAdmin(true);

        AdminMfaChallengeResponse response = service.login(new AdminLoginRequest(EMAIL, PASSWORD));

        assertThat(response.challengeToken()).isEqualTo(CHALLENGE_TOKEN);
        assertThat(response.expiresInSeconds()).isEqualTo(300);
        assertThat(response.enrolmentRequired()).isFalse();
    }

    /** The DoD in one assertion: no access token is minted anywhere on this path. */
    @Test
    void neverIssuesAnAccessToken() {
        givenUserExists();
        givenPasswordIsCorrect();
        givenActiveAdmin(true);

        service.login(new AdminLoginRequest(EMAIL, PASSWORD));

        verify(jwtService, never()).generateAccessToken(anyString(), org.mockito.ArgumentMatchers.anyInt());
        verify(jwtService, never()).generateAccessToken(anyString(), org.mockito.ArgumentMatchers.anyInt(), any());
        verify(jwtService, never()).generateRefreshToken(anyString());
    }

    /** An admin who has never confirmed TOTP is sent to enrolment rather than a code prompt. */
    @Test
    void flagsEnrolmentWhenTotpWasNeverConfirmed() {
        givenUserExists();
        givenPasswordIsCorrect();
        givenActiveAdmin(false);

        assertThat(service.login(new AdminLoginRequest(EMAIL, PASSWORD)).enrolmentRequired()).isTrue();
    }

    @Test
    void clearsTheFailureCounterOnceThePasswordStepSucceeds() {
        givenUserExists();
        givenPasswordIsCorrect();
        givenActiveAdmin(true);

        service.login(new AdminLoginRequest(EMAIL, PASSWORD));

        verify(authRateLimitService).clearAdminLoginFailures(eq(EMAIL), any());
    }

    // -----------------------------------------------------------------------------------
    // AC#2 - every rejection is indistinguishable.
    //
    // Regression test for the enumeration hole: if these ever diverge, the endpoint tells an
    // attacker which addresses are administrators.
    // -----------------------------------------------------------------------------------

    @Test
    void wrongPasswordAndNonAdminUserProduceIdenticalRejections() {
        Throwable wrongPassword = catchThrowable(() -> {
            givenUserExists();
            givenPasswordIsWrong();
            service.login(new AdminLoginRequest(EMAIL, PASSWORD));
        });

        Throwable notAnAdmin = catchThrowable(() -> {
            givenUserExists();
            givenPasswordIsCorrect();
            when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.empty());
            service.login(new AdminLoginRequest(EMAIL, PASSWORD));
        });

        assertThat(wrongPassword).isInstanceOf(UnauthorizedException.class);
        assertThat(notAnAdmin).isInstanceOf(UnauthorizedException.class);
        assertThat(notAnAdmin.getMessage()).isEqualTo(wrongPassword.getMessage());
        assertThat(notAnAdmin.getClass()).isEqualTo(wrongPassword.getClass());
    }

    @Test
    void unknownEmailIsIndistinguishableFromAWrongPassword() {
        Throwable wrongPassword = catchThrowable(() -> {
            givenUserExists();
            givenPasswordIsWrong();
            service.login(new AdminLoginRequest(EMAIL, PASSWORD));
        });

        Throwable unknownEmail = catchThrowable(() -> {
            when(userRepository.findByEmail("nobody@genlink.kh")).thenReturn(Optional.empty());
            service.login(new AdminLoginRequest("nobody@genlink.kh", PASSWORD));
        });

        assertThat(unknownEmail).isInstanceOf(UnauthorizedException.class);
        assertThat(unknownEmail.getMessage()).isEqualTo(wrongPassword.getMessage());
    }

    /**
     * The admin lookup must not happen before the password is verified, or the endpoint answers
     * "is this an admin?" for free, without any credential and faster than a real attempt.
     */
    @Test
    void doesNotConsultTheAdminTableUntilThePasswordIsVerified() {
        givenUserExists();
        givenPasswordIsWrong();

        assertThatThrownBy(() -> service.login(new AdminLoginRequest(EMAIL, PASSWORD)))
            .isInstanceOf(UnauthorizedException.class);

        verify(adminUserRepository, never()).findActiveByUserEmail(anyString());
    }

    /**
     * Timing: an unknown address must still spend a password comparison, or it answers in a
     * millisecond where a real attempt takes a hundred and the two are separable with a
     * stopwatch. Asserted structurally rather than by measuring the clock - a timing assertion
     * would be flaky on CI and would tell us less.
     */
    @Test
    void spendsAPasswordComparisonEvenWhenTheAddressIsUnknown() {
        PasswordEncoder countingEncoder = new CountingEncoder();
        AdminAuthServiceImpl serviceWithCounter = new AdminAuthServiceImpl(authenticationManager,
            userRepository, adminUserRepository, authRateLimitService, securityAuditService,
            jwtService, new JwtProperties(), countingEncoder);
        when(userRepository.findByEmail("nobody@genlink.kh")).thenReturn(Optional.empty());

        int before = ((CountingEncoder) countingEncoder).matchCount;
        assertThatThrownBy(() -> serviceWithCounter.login(new AdminLoginRequest("nobody@genlink.kh", PASSWORD)))
            .isInstanceOf(UnauthorizedException.class);

        assertThat(((CountingEncoder) countingEncoder).matchCount)
            .as("a decoy hash comparison must run for an address with no account")
            .isEqualTo(before + 1);
    }

    // -----------------------------------------------------------------------------------
    // AC#4 - every failure is auditable.
    // -----------------------------------------------------------------------------------

    @Test
    void writesAnAuditRowWhenThePasswordIsWrong() {
        givenUserExists();
        givenPasswordIsWrong();

        assertThatThrownBy(() -> service.login(new AdminLoginRequest(EMAIL, PASSWORD)))
            .isInstanceOf(UnauthorizedException.class);

        verify(securityAuditService).logFailure(eq("ADMIN_LOGIN_FAILED"), any(User.class), eq(EMAIL), anyString());
        verify(authRateLimitService).recordAdminLoginFailure(eq(EMAIL), any());
    }

    /** No user row to attach, so the overload without one is used - but a row is still written. */
    @Test
    void writesAnAuditRowWhenTheAddressIsUnknown() {
        when(userRepository.findByEmail("nobody@genlink.kh")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new AdminLoginRequest("nobody@genlink.kh", PASSWORD)))
            .isInstanceOf(UnauthorizedException.class);

        verify(securityAuditService).logFailure(eq("ADMIN_LOGIN_FAILED"), eq("nobody@genlink.kh"), anyString());
    }

    @Test
    void writesAnAuditRowWhenAnAccountIsNotAnAdmin() {
        givenUserExists();
        givenPasswordIsCorrect();
        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.login(new AdminLoginRequest(EMAIL, PASSWORD)))
            .isInstanceOf(UnauthorizedException.class);

        verify(securityAuditService).logFailure(eq("ADMIN_LOGIN_FAILED"), any(User.class), eq(EMAIL), anyString());
    }

    /**
     * The audit row carries the real reason even though the response does not. That asymmetry
     * is the point: operators can tell the cases apart, callers cannot.
     */
    @Test
    void recordsTheRealReasonInTheAuditTrailButNotInTheResponse() {
        givenUserExists();
        givenPasswordIsCorrect();
        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.empty());

        Throwable thrown = catchThrowable(() -> service.login(new AdminLoginRequest(EMAIL, PASSWORD)));

        verify(securityAuditService).logFailure(anyString(), any(User.class), anyString(), eq("NOT_AN_ACTIVE_ADMIN"));
        assertThat(thrown.getMessage()).doesNotContain("ADMIN").doesNotContain("admin");
    }

    // -----------------------------------------------------------------------------------
    // Rate limiting (SSOT §10.6)
    // -----------------------------------------------------------------------------------

    @Test
    void refusesWithRateLimitedOnceTheAdminBucketIsBlocked() {
        when(authRateLimitService.isAdminLoginBlocked(eq(EMAIL), any())).thenReturn(true);

        assertThatThrownBy(() -> service.login(new AdminLoginRequest(EMAIL, PASSWORD)))
            .isInstanceOf(RateLimitedException.class);

        // Blocked before any credential work: no lookup, no hash comparison.
        verify(userRepository, never()).findByEmail(anyString());
        verify(securityAuditService).logFailure(eq("ADMIN_LOGIN_BLOCKED"), eq(EMAIL), anyString());
    }

    // -----------------------------------------------------------------------------------

    private void givenUserExists() {
        User user = new User();
        user.setEmail(EMAIL);
        user.setPasswordHash(passwordEncoder.encode(PASSWORD));
        when(userRepository.findByEmail(EMAIL)).thenReturn(Optional.of(user));
    }

    /*
     * doReturn/doThrow rather than when(...): the tests that compare two rejections re-stub
     * this mock mid-method, and the when(...) form calls the mock to record the stub - which
     * throws the exception configured a moment earlier, failing the test on its own setup.
     */
    private void givenPasswordIsCorrect() {
        org.mockito.Mockito.doReturn(null).when(authenticationManager).authenticate(any());
    }

    private void givenPasswordIsWrong() {
        org.mockito.Mockito.doThrow(new BadCredentialsException("bad"))
            .when(authenticationManager).authenticate(any());
    }

    private void givenActiveAdmin(boolean enrolled) {
        AdminUser admin = new AdminUser();
        admin.setActive(true);
        if (enrolled) {
            admin.setTotpConfirmedAt(java.time.Instant.now());
        }
        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.of(admin));
    }

    /** Counts hash comparisons so the timing decoy can be asserted without a stopwatch. */
    private static final class CountingEncoder implements PasswordEncoder {
        private final PasswordEncoder delegate = new BCryptPasswordEncoder(4);
        private int matchCount;

        @Override
        public String encode(CharSequence rawPassword) {
            return delegate.encode(rawPassword);
        }

        @Override
        public boolean matches(CharSequence rawPassword, String encodedPassword) {
            matchCount++;
            return delegate.matches(rawPassword, encodedPassword);
        }
    }
}
