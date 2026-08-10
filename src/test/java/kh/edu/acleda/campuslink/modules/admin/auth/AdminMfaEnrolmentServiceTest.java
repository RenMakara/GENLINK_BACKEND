package kh.edu.acleda.campuslink.modules.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.MfaConfirmResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.MfaEnrolResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.Base32;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.RecoveryCodeService;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.TotpGenerator;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.crypto.CryptoProperties;
import kh.edu.acleda.campuslink.shared.crypto.FieldEncryptor;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import kh.edu.acleda.campuslink.shared.security.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADM-022 enrolment flow.
 *
 * <p>Crypto is real here - the encryptor, the TOTP generator and BCrypt all run for real, with
 * only the repository, the token service and auditing stubbed. Mocking the encryptor would let
 * "encrypted" mean whatever the mock returned, and AC#2 is precisely the claim that it does not.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminMfaEnrolmentServiceTest {

    private static final String EMAIL = "admin@genlink.kh";
    private static final String CHALLENGE = "challenge.jwt.token";

    @Mock private AdminUserRepository adminUserRepository;
    @Mock private JwtService jwtService;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private Environment environment;

    private AdminMfaEnrolmentService service;
    private FieldEncryptor fieldEncryptor;
    private TotpGenerator totpGenerator;
    private AdminUser admin;

    @BeforeEach
    void setUp() {
        CryptoProperties properties = new CryptoProperties();
        properties.setSecretKey(Base64.getEncoder().encodeToString("test-only-key-32-bytes-exactly!!".getBytes()));
        fieldEncryptor = new FieldEncryptor(properties, environment);
        totpGenerator = new TotpGenerator();

        service = new AdminMfaEnrolmentService(adminUserRepository, jwtService, fieldEncryptor,
            totpGenerator, new RecoveryCodeService(new BCryptPasswordEncoder(4), new ObjectMapper()),
            securityAuditService);

        User user = new User();
        user.setEmail(EMAIL);
        admin = new AdminUser();
        admin.setUser(user);
        admin.setActive(true);

        when(jwtService.validateToken(CHALLENGE)).thenReturn(true);
        when(jwtService.getTokenType(CHALLENGE)).thenReturn(TokenType.ADMIN_MFA_CHALLENGE);
        when(jwtService.getUsername(CHALLENGE)).thenReturn(EMAIL);
        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.of(admin));
    }

    // -------------------------------------------------------------------------------
    // AC#1 - the otpauth:// URI is what an authenticator app expects.
    // -------------------------------------------------------------------------------

    /**
     * Conformance to the Key Uri Format, which is the closest thing to "a standard app accepts
     * it" that a unit test can assert. The end-to-end check - scanning it with a real
     * authenticator - is a manual step and is listed in the report.
     */
    @Test
    void buildsAConformantOtpauthUri() {
        MfaEnrolResponse response = service.enrol(CHALLENGE);
        URI uri = URI.create(response.otpauthUri());

        assertThat(uri.getScheme()).isEqualTo("otpauth");
        assertThat(uri.getHost()).isEqualTo("totp");

        Map<String, String> params = queryParams(response.otpauthUri());
        assertThat(params).containsEntry("algorithm", "SHA1")
            .containsEntry("digits", "6")
            .containsEntry("period", "30")
            .containsEntry("issuer", "GenLink KH Admin")
            .containsEntry("secret", response.secret());
    }

    /** The label identifies which account the code belongs to, for admins with several. */
    @Test
    void labelsTheEntryWithIssuerAndAccount() {
        String uri = service.enrol(CHALLENGE).otpauthUri();
        assertThat(uri).contains("GenLink%20KH%20Admin:admin@genlink.kh");
        // '+' would be shown literally by an app that follows the spec.
        assertThat(uri.substring(0, uri.indexOf('?'))).doesNotContain("+");
    }

    /** The secret in the URI must actually drive the codes, or the QR is decorative. */
    @Test
    void theSecretInTheUriGeneratesCodesTheServerAccepts() {
        MfaEnrolResponse response = service.enrol(CHALLENGE);
        byte[] secret = Base32.decode(queryParams(response.otpauthUri()).get("secret"));

        Instant now = Instant.now();
        assertThat(totpGenerator.verify(secret, totpGenerator.generate(secret, now), now)).isTrue();
    }

    // -------------------------------------------------------------------------------
    // AC#2 - the stored secret is unreadable.
    // -------------------------------------------------------------------------------

    @Test
    void persistsTheSecretEncryptedNotAsGiven() {
        MfaEnrolResponse response = service.enrol(CHALLENGE);

        assertThat(admin.getTotpSecret()).isNotNull().isNotEqualTo(response.secret()).startsWith("v1:");
        assertThat(fieldEncryptor.decrypt(admin.getTotpSecret())).isEqualTo(response.secret());
    }

    /** Nothing is switched on until the admin proves their app produces matching codes. */
    @Test
    void doesNotEnableTotpUntilConfirmed() {
        service.enrol(CHALLENGE);

        assertThat(admin.isTotpEnabled()).isFalse();
        assertThat(admin.getTotpConfirmedAt()).isNull();
        assertThat(admin.isEnrolled()).isFalse();
    }

    /** An admin who lost the QR page must be able to start over; an unconfirmed secret grants nothing. */
    @Test
    void re_enrolBeforeConfirmingReplacesTheSecret() {
        String first = service.enrol(CHALLENGE).secret();
        String second = service.enrol(CHALLENGE).secret();

        assertThat(second).isNotEqualTo(first);
        assertThat(fieldEncryptor.decrypt(admin.getTotpSecret())).isEqualTo(second);
    }

    // -------------------------------------------------------------------------------
    // Confirm
    // -------------------------------------------------------------------------------

    @Test
    void confirmingWithAValidCodeEnablesTotp() {
        String code = codeFor(service.enrol(CHALLENGE).secret());

        MfaConfirmResponse response = service.confirm(CHALLENGE, code);

        assertThat(admin.isTotpEnabled()).isTrue();
        assertThat(admin.getTotpConfirmedAt()).isNotNull();
        assertThat(admin.isEnrolled()).isTrue();
        assertThat(response.recoveryCodes()).hasSize(10);
    }

    @Test
    void confirmingWithAWrongCodeChangesNothing() {
        service.enrol(CHALLENGE);

        assertThatThrownBy(() -> service.confirm(CHALLENGE, "000000"))
            .isInstanceOf(UnauthorizedException.class);

        assertThat(admin.isTotpEnabled()).isFalse();
        assertThat(admin.getTotpConfirmedAt()).isNull();
        assertThat(admin.getRecoveryCodes()).isNull();
    }

    @Test
    void cannotConfirmWithoutEnrollingFirst() {
        assertThatThrownBy(() -> service.confirm(CHALLENGE, "123456"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Start enrolment");
    }

    // -------------------------------------------------------------------------------
    // AC#3 - recovery codes are returned exactly once.
    // -------------------------------------------------------------------------------

    @Test
    void recoveryCodesAreIssuedOnlyAtConfirmNotAtEnrol() {
        MfaEnrolResponse response = service.enrol(CHALLENGE);

        // Nothing in the enrol response could carry them, and none are persisted yet.
        assertThat(response.secret()).isNotNull();
        assertThat(admin.getRecoveryCodes()).isNull();
    }

    @Test
    void confirmingASecondTimeIsRefusedSoCodesCannotBeReissued() {
        service.confirm(CHALLENGE, codeFor(service.enrol(CHALLENGE).secret()));
        String issued = admin.getRecoveryCodes();

        assertThatThrownBy(() -> service.confirm(CHALLENGE, "123456"))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("already set up");

        assertThat(admin.getRecoveryCodes()).isEqualTo(issued);
    }

    @Test
    void anEnrolledAdminCannotStartEnrolmentAgain() {
        admin.setTotpConfirmedAt(Instant.now());

        assertThatThrownBy(() -> service.enrol(CHALLENGE))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("already set up");
    }

    @Test
    void storedRecoveryCodesAreHashedNotPlaintext() {
        MfaConfirmResponse response = service.confirm(CHALLENGE, codeFor(service.enrol(CHALLENGE).secret()));

        for (String code : response.recoveryCodes()) {
            assertThat(admin.getRecoveryCodes()).doesNotContain(code);
        }
    }

    // -------------------------------------------------------------------------------
    // These routes are permitAll, so this check is the only thing guarding them.
    // -------------------------------------------------------------------------------

    @Test
    void rejectsAnAdminAccessTokenWhereAChallengeIsRequired() {
        when(jwtService.getTokenType(CHALLENGE)).thenReturn(TokenType.ADMIN);

        assertThatThrownBy(() -> service.enrol(CHALLENGE)).isInstanceOf(UnauthorizedException.class);
        verify(adminUserRepository, never()).save(any());
    }

    @Test
    void rejectsAMobileToken() {
        when(jwtService.getTokenType(CHALLENGE)).thenReturn(TokenType.MOBILE);
        assertThatThrownBy(() -> service.enrol(CHALLENGE)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsAnInvalidOrExpiredToken() {
        when(jwtService.validateToken(CHALLENGE)).thenReturn(false);
        assertThatThrownBy(() -> service.enrol(CHALLENGE)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsAMissingToken() {
        assertThatThrownBy(() -> service.enrol(null)).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.enrol("  ")).isInstanceOf(UnauthorizedException.class);
    }

    /** A token naming someone whose admin access was revoked between login and enrolment. */
    @Test
    void rejectsAChallengeForAnAccountThatIsNoLongerAnAdmin() {
        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.enrol(CHALLENGE)).isInstanceOf(UnauthorizedException.class);
    }

    // -------------------------------------------------------------------------------

    private String codeFor(String base32Secret) {
        return totpGenerator.generate(Base32.decode(base32Secret), Instant.now());
    }

    private static Map<String, String> queryParams(String uri) {
        String query = uri.substring(uri.indexOf('?') + 1);
        return Stream.of(query.split("&"))
            .map(pair -> pair.split("=", 2))
            .collect(Collectors.toMap(
                pair -> pair[0],
                pair -> java.net.URLDecoder.decode(pair[1], java.nio.charset.StandardCharsets.UTF_8)));
    }
}
