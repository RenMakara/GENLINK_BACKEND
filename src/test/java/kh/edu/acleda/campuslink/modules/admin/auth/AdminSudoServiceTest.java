package kh.edu.acleda.campuslink.modules.admin.auth;

import java.time.Instant;
import java.util.Base64;
import java.util.Optional;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.request.SudoRequest;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.Base32;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.TotpGenerator;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.crypto.CryptoProperties;
import kh.edu.acleda.campuslink.shared.crypto.FieldEncryptor;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** ADM-025 - step-up re-authentication. */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminSudoServiceTest {

    private static final String EMAIL = "admin@genlink.kh";
    private static final String ACCESS_TOKEN = "admin.access.token";
    private static final String SESSION = "session-1";
    private static final String PASSWORD = "correct-horse-battery-staple";

    @Mock private AuthenticationManager authenticationManager;
    @Mock private AdminUserRepository adminUserRepository;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private JwtService jwtService;
    @Mock private Environment environment;

    private AdminSudoRegistry registry;
    private AdminSudoService service;
    private TotpGenerator totpGenerator;
    private byte[] secret;

    @BeforeEach
    void setUp() {
        registry = new AdminSudoRegistry();
        totpGenerator = new TotpGenerator();
        secret = totpGenerator.generateSecret();

        CryptoProperties properties = new CryptoProperties();
        properties.setSecretKey(Base64.getEncoder().encodeToString("test-only-key-32-bytes-exactly!!".getBytes()));
        FieldEncryptor encryptor = new FieldEncryptor(properties, environment);

        service = new AdminSudoService(authenticationManager, adminUserRepository, registry,
            totpGenerator, encryptor, securityAuditService, jwtService);

        User user = new User();
        user.setEmail(EMAIL);
        AdminUser admin = new AdminUser();
        admin.setUser(user);
        admin.setActive(true);
        admin.setTotpEnabled(true);
        admin.setTotpConfirmedAt(Instant.now());
        admin.setTotpSecret(encryptor.encrypt(Base32.encode(secret)));

        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.of(admin));
        when(jwtService.validateToken(ACCESS_TOKEN)).thenReturn(true);
        when(jwtService.getTokenId(ACCESS_TOKEN)).thenReturn(SESSION);

        authenticate();
        bindRequestWithToken(ACCESS_TOKEN);
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
        RequestContextHolder.resetRequestAttributes();
    }

    private void authenticate() {
        SecurityContextHolder.getContext().setAuthentication(
            new UsernamePasswordAuthenticationToken(EMAIL, null,
                java.util.List.of(new SimpleGrantedAuthority("PERM_user:delete"))));
    }

    private void bindRequestWithToken(String token) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        if (token != null) {
            request.addHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private String currentCode() {
        return totpGenerator.generate(secret, Instant.now());
    }

    // Opening the window
    @Test
    void aValidTotpCodeOpensTheWindow() {
        assertThat(service.isOpen()).isFalse();

        assertThat(service.open(new SudoRequest(currentCode(), null)).expiresInSeconds()).isEqualTo(300);

        assertThat(service.isOpen()).isTrue();
    }

    @Test
    void aValidPasswordOpensTheWindow() {
        org.mockito.Mockito.doReturn(null).when(authenticationManager).authenticate(any());

        service.open(new SudoRequest(null, PASSWORD));

        assertThat(service.isOpen()).isTrue();
    }

    @Test
    void aWrongCodeIsRefusedAndOpensNothing() {
        assertThatThrownBy(() -> service.open(new SudoRequest("000000", null)))
            .isInstanceOf(UnauthorizedException.class);

        assertThat(service.isOpen()).isFalse();
    }

    @Test
    void aWrongPasswordIsRefusedAndOpensNothing() {
        org.mockito.Mockito.doThrow(new BadCredentialsException("bad"))
            .when(authenticationManager).authenticate(any());

        assertThatThrownBy(() -> service.open(new SudoRequest(null, "wrong")))
            .isInstanceOf(UnauthorizedException.class);

        assertThat(service.isOpen()).isFalse();
    }

    /** Neither factor supplied is a refusal, not an accidentally-open window. */
    @Test
    void anEmptyRequestIsRefused() {
        assertThatThrownBy(() -> service.open(new SudoRequest(null, null)))
            .isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.open(new SudoRequest("", "")))
            .isInstanceOf(UnauthorizedException.class);

        assertThat(service.isOpen()).isFalse();
    }

    @Test
    void bothOutcomesAreAudited() {
        service.open(new SudoRequest(currentCode(), null));
        verify(securityAuditService).logSuccess(eq("ADMIN_SUDO_OPENED"), any(User.class), eq(EMAIL));

        assertThatThrownBy(() -> service.open(new SudoRequest("000000", null)))
            .isInstanceOf(UnauthorizedException.class);
        verify(securityAuditService).logFailure(eq("ADMIN_SUDO_FAILED"), any(User.class), eq(EMAIL), anyString());
    }

    // AC#3 - per session
    /** The scenario the whole control exists for: re-authenticating here must not arm a browser left open somewhere else. */
    @Test
    void aSecondSessionDoesNotInheritTheWindow() {
        service.open(new SudoRequest(currentCode(), null));
        assertThat(service.isOpen()).isTrue();

        // Same admin, different device - a different access token, so a different jti.
        when(jwtService.validateToken("other.token")).thenReturn(true);
        when(jwtService.getTokenId("other.token")).thenReturn("session-2");
        bindRequestWithToken("other.token");

        assertThat(service.isOpen()).isFalse();
    }

    // Session resolution
    @Test
    void refusesToOpenAWindowWithoutAResolvableSession() {
        bindRequestWithToken(null);

        assertThatThrownBy(() -> service.open(new SudoRequest(currentCode(), null)))
            .isInstanceOf(UnauthorizedException.class);
    }

    /** A token predating session ids carries no jti; there is nothing to scope a window to. */
    @Test
    void refusesWhenTheTokenCarriesNoSessionId() {
        when(jwtService.getTokenId(ACCESS_TOKEN)).thenReturn(null);

        assertThatThrownBy(() -> service.open(new SudoRequest(currentCode(), null)))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void isOpenIsFalseOutsideARequest() {
        RequestContextHolder.resetRequestAttributes();
        assertThat(service.isOpen()).isFalse();
    }

    @Test
    void refusesAnAccountThatIsNoLongerAnAdmin() {
        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.open(new SudoRequest(currentCode(), null)))
            .isInstanceOf(UnauthorizedException.class);
    }

    /** A code from a different secret must not open anything. */
    @Test
    void doesNotAcceptACodeFromAnotherSecret() {
        byte[] other = totpGenerator.generateSecret();

        assertThatThrownBy(() -> service.open(
            new SudoRequest(totpGenerator.generate(other, Instant.now()), null)))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void reportsSecondsRemaining() {
        assertThat(service.secondsRemaining()).isZero();
        service.open(new SudoRequest(currentCode(), null));
        assertThat(service.secondsRemaining()).isBetween(295L, 300L);
    }

    @Test
    void closingEndsTheWindow() {
        service.open(new SudoRequest(currentCode(), null));

        service.close(SESSION);

        assertThat(service.isOpen()).isFalse();
    }
}
