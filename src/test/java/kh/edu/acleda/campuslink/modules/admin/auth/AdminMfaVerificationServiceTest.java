package kh.edu.acleda.campuslink.modules.admin.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.auth.dto.response.AdminTokenResponse;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.Base32;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.RecoveryCodeService;
import kh.edu.acleda.campuslink.modules.admin.auth.totp.TotpGenerator;
import kh.edu.acleda.campuslink.modules.admin.rbac.entity.AdminUser;
import kh.edu.acleda.campuslink.modules.admin.rbac.repository.AdminUserRepository;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.crypto.CryptoProperties;
import kh.edu.acleda.campuslink.shared.crypto.FieldEncryptor;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.ForbiddenException;
import kh.edu.acleda.campuslink.shared.exception.RateLimitedException;
import kh.edu.acleda.campuslink.shared.exception.UnauthorizedException;
import kh.edu.acleda.campuslink.shared.security.JwtProperties;
import kh.edu.acleda.campuslink.shared.security.JwtService;
import kh.edu.acleda.campuslink.shared.security.TokenType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.core.env.Environment;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * ADM-023. Real crypto throughout - encryptor, TOTP and BCrypt all run for real, so "a valid
 * code" means a code the production algorithm actually produced.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AdminMfaVerificationServiceTest {

    private static final String EMAIL = "admin@genlink.kh";
    private static final String CHALLENGE = "challenge.jwt.token";
    private static final String CHALLENGE_ID = "challenge-1";
    private static final String ACCESS_TOKEN = "admin.access.token";
    private static final String REFRESH_TOKEN = "admin.refresh.token";

    @Mock private AdminUserRepository adminUserRepository;
    @Mock private JwtService jwtService;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private Environment environment;

    private AdminMfaVerificationService service;
    private AdminMfaChallengeStore challengeStore;
    private RecoveryCodeService recoveryCodeService;
    private TotpGenerator totpGenerator;
    private JwtProperties jwtProperties;
    private AdminUser admin;
    private byte[] secret;
    private List<String> recoveryCodes;

    @BeforeEach
    void setUp() {
        CryptoProperties cryptoProperties = new CryptoProperties();
        cryptoProperties.setSecretKey(
            Base64.getEncoder().encodeToString("test-only-key-32-bytes-exactly!!".getBytes()));
        FieldEncryptor fieldEncryptor = new FieldEncryptor(cryptoProperties, environment);

        totpGenerator = new TotpGenerator();
        recoveryCodeService = new RecoveryCodeService(new BCryptPasswordEncoder(4), new ObjectMapper());
        challengeStore = new AdminMfaChallengeStore();
        jwtProperties = new JwtProperties();

        service = new AdminMfaVerificationService(adminUserRepository, challengeStore, jwtService,
            jwtProperties, fieldEncryptor, totpGenerator, recoveryCodeService, refreshTokenService,
            securityAuditService);

        secret = totpGenerator.generateSecret();
        RecoveryCodeService.GeneratedCodes codes = recoveryCodeService.generate();
        recoveryCodes = codes.plaintext();

        User user = new User();
        user.setEmail(EMAIL);
        user.setTokenVersion(3);

        admin = new AdminUser();
        admin.setUser(user);
        admin.setActive(true);
        admin.setTotpSecret(fieldEncryptor.encrypt(Base32.encode(secret)));
        admin.setTotpEnabled(true);
        admin.setTotpConfirmedAt(Instant.now());
        admin.setRecoveryCodes(codes.json());

        when(jwtService.validateToken(CHALLENGE)).thenReturn(true);
        when(jwtService.getTokenType(CHALLENGE)).thenReturn(TokenType.ADMIN_MFA_CHALLENGE);
        when(jwtService.getUsername(CHALLENGE)).thenReturn(EMAIL);
        when(jwtService.getTokenId(CHALLENGE)).thenReturn(CHALLENGE_ID);
        when(jwtService.generateAdminAccessToken(anyString(), org.mockito.ArgumentMatchers.anyInt(), anyString()))
            .thenReturn(ACCESS_TOKEN);
        when(refreshTokenService.create(any(), any(), any(), any(), anyLong())).thenReturn(REFRESH_TOKEN);
        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.of(admin));
    }

    private String currentCode() {
        return totpGenerator.generate(secret, Instant.now());
    }

    // -------------------------------------------------------------------------------
    // AC#1 - a correct code issues a typ=ADMIN token with a 10-minute TTL.
    // -------------------------------------------------------------------------------

    @Test
    void issuesAdminTokensForAValidTotpCode() {
        AdminTokenResponse response = service.verify(CHALLENGE, currentCode());

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.refreshToken()).isEqualTo(REFRESH_TOKEN);
        assertThat(response.tokenType()).isEqualTo("Bearer");
        assertThat(response.expiresInSeconds()).isEqualTo(600);
    }

    /**
     * The type claim is what confines the token to /api/v1/admin/**.
     *
     * <p>Asserted through {@code generateAdminAccessToken}, which always stamps
     * {@code typ=ADMIN} - that the claim really is written is pinned one level down, in
     * {@code JwtTokenProviderTest}. The dedicated method exists because ADM-024 needs a session
     * id on the token so logout can revoke this session without touching the others.
     */
    @Test
    void theAccessTokenIsIssuedWithTheAdminType() {
        service.verify(CHALLENGE, currentCode());

        verify(jwtService).generateAdminAccessToken(eq(EMAIL), eq(3), anyString());
        verify(jwtService, never()).generateAccessToken(anyString(),
            org.mockito.ArgumentMatchers.anyInt(), eq(TokenType.MOBILE));
    }

    /** Each sign-in gets its own session id, so signing out of one leaves the others alone. */
    @Test
    void theAccessTokenCarriesAUniqueSessionId() {
        ArgumentCaptor<String> sessionId = ArgumentCaptor.forClass(String.class);

        service.verify(CHALLENGE, currentCode());

        verify(jwtService).generateAdminAccessToken(eq(EMAIL), eq(3), sessionId.capture());
        assertThat(sessionId.getValue()).isNotBlank();
        assertThat(java.util.UUID.fromString(sessionId.getValue())).isNotNull();
    }

    /** 8 hours, not the mobile app's 7 days: a stolen admin session reaches everyone's data. */
    @Test
    void theRefreshTokenUsesTheAdminLifetime() {
        service.verify(CHALLENGE, currentCode());

        verify(refreshTokenService).create(any(), any(), any(), any(), eq(28_800_000L));
        assertThat(jwtProperties.getAdminRefreshTokenExpirationMs()).isEqualTo(28_800_000L);
    }

    @Test
    void stampsLastLogin() {
        service.verify(CHALLENGE, currentCode());

        assertThat(admin.getLastLoginAt()).isNotNull();
    }

    @Test
    void auditsTheSuccessfulSignIn() {
        service.verify(CHALLENGE, currentCode());

        verify(securityAuditService).logSuccess(eq("ADMIN_MFA_VERIFIED"), any(User.class), eq(EMAIL));
    }

    /** TOTP sign-in tells the client nothing about recovery codes; it did not touch them. */
    @Test
    void doesNotReportRecoveryCountAfterATotpSignIn() {
        assertThat(service.verify(CHALLENGE, currentCode()).recoveryCodesRemaining()).isNull();
    }

    // -------------------------------------------------------------------------------
    // AC#2 - a reused recovery code is rejected.
    // -------------------------------------------------------------------------------

    @Test
    void acceptsARecoveryCode() {
        AdminTokenResponse response = service.verify(CHALLENGE, recoveryCodes.get(0));

        assertThat(response.accessToken()).isEqualTo(ACCESS_TOKEN);
        assertThat(response.recoveryCodesRemaining()).isEqualTo(9);
    }

    @Test
    void rejectsARecoveryCodeOnSecondUse() {
        service.verify(CHALLENGE, recoveryCodes.get(0));
        // A fresh challenge: the first one was retired by the successful sign-in above, so
        // reusing it would fail for the wrong reason and prove nothing about the code.
        when(jwtService.getTokenId(CHALLENGE)).thenReturn("challenge-2");

        assertThatThrownBy(() -> service.verify(CHALLENGE, recoveryCodes.get(0)))
            .isInstanceOf(UnauthorizedException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                .isEqualTo(AdminErrorCode.ADMIN_MFA_INVALID));
    }

    /** Spending one must leave the other nine working - the reason for per-code storage. */
    @Test
    void theOtherRecoveryCodesStillWorkAfterOneIsSpent() {
        service.verify(CHALLENGE, recoveryCodes.get(0));
        when(jwtService.getTokenId(CHALLENGE)).thenReturn("challenge-2");

        assertThat(service.verify(CHALLENGE, recoveryCodes.get(1)).recoveryCodesRemaining()).isEqualTo(8);
    }

    /** The spend has to be persisted, or reuse is only prevented until the next restart. */
    @Test
    void persistsTheConsumedRecoveryCode() {
        String before = admin.getRecoveryCodes();

        service.verify(CHALLENGE, recoveryCodes.get(0));

        assertThat(admin.getRecoveryCodes()).isNotEqualTo(before);
        verify(adminUserRepository, org.mockito.Mockito.atLeastOnce()).save(admin);
    }

    // -------------------------------------------------------------------------------
    // AC#3 - an unenrolled admin is refused.
    // -------------------------------------------------------------------------------

    @Test
    void refusesAnUnenrolledAdminWithNotEnrolled() {
        admin.setTotpConfirmedAt(null);

        assertThatThrownBy(() -> service.verify(CHALLENGE, "123456"))
            .isInstanceOf(ForbiddenException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                .isEqualTo(AdminErrorCode.ADMIN_MFA_NOT_ENROLLED));

        verify(jwtService, never()).generateAdminAccessToken(anyString(),
            org.mockito.ArgumentMatchers.anyInt(), anyString());
    }

    /** A spent challenge is a replay, not a rate limit - the two must stay distinguishable. */
    @Test
    void aSpentChallengeStillReportsExpiredRatherThanRateLimited() {
        service.verify(CHALLENGE, currentCode());

        assertThatThrownBy(() -> service.verify(CHALLENGE, currentCode()))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("expired");
    }

    // -------------------------------------------------------------------------------
    // AC#4 - five wrong codes void the challenge.
    // -------------------------------------------------------------------------------

    @Test
    void fiveWrongCodesVoidTheChallengeSoTheSixthCannotSucceed() {
        for (int attempt = 0; attempt < 5; attempt++) {
            assertThatThrownBy(() -> service.verify(CHALLENGE, "000000"))
                .isInstanceOf(UnauthorizedException.class);
        }

        // The sixth carries a CORRECT code and must still be refused - otherwise the cap only
        // limits how many wrong guesses fit, not how long the challenge stays attackable.
        //
        // 429 RATE_LIMITED, not 401: ADM-027 separated "you ran out of attempts" from "this
        // challenge was already spent", because they need different answers from the portal.
        assertThatThrownBy(() -> service.verify(CHALLENGE, currentCode()))
            .isInstanceOf(RateLimitedException.class)
            .hasFieldOrPropertyWithValue("errorCode", "RATE_LIMITED");
        verify(jwtService, never()).generateAdminAccessToken(anyString(),
            org.mockito.ArgumentMatchers.anyInt(), anyString());
    }

    @Test
    void aWrongCodeIsReportedAsMfaInvalid() {
        assertThatThrownBy(() -> service.verify(CHALLENGE, "000000"))
            .isInstanceOf(UnauthorizedException.class)
            .satisfies(ex -> assertThat(((ApiException) ex).getErrorCode())
                .isEqualTo(AdminErrorCode.ADMIN_MFA_INVALID));
    }

    // -------------------------------------------------------------------------------
    // AC#5 - every failure writes a security_audit_logs row.
    // -------------------------------------------------------------------------------

    @Test
    void auditsAWrongCode() {
        assertThatThrownBy(() -> service.verify(CHALLENGE, "000000")).isInstanceOf(RuntimeException.class);

        verify(securityAuditService).logFailure(eq("ADMIN_MFA_FAILED"), any(User.class), eq(EMAIL), anyString());
    }

    @Test
    void auditsAnUnenrolledAttempt() {
        admin.setTotpConfirmedAt(null);

        assertThatThrownBy(() -> service.verify(CHALLENGE, "000000")).isInstanceOf(RuntimeException.class);

        verify(securityAuditService).logFailure(eq("ADMIN_MFA_FAILED"), eq(EMAIL), eq("NOT_ENROLLED"));
    }

    @Test
    void auditsAnAttemptOnAVoidedChallenge() {
        challengeStore.spend(CHALLENGE_ID);

        assertThatThrownBy(() -> service.verify(CHALLENGE, currentCode())).isInstanceOf(RuntimeException.class);

        verify(securityAuditService).logFailure(eq("ADMIN_MFA_FAILED"), eq(EMAIL), eq("CHALLENGE_VOID"));
    }

    // -------------------------------------------------------------------------------
    // Single use (a decision, not in the SSOT - see AdminMfaChallengeStore).
    // -------------------------------------------------------------------------------

    @Test
    void aChallengeCannotBeReplayedAfterASuccessfulSignIn() {
        service.verify(CHALLENGE, currentCode());

        assertThatThrownBy(() -> service.verify(CHALLENGE, currentCode()))
            .isInstanceOf(UnauthorizedException.class)
            .hasMessageContaining("expired");
    }

    // -------------------------------------------------------------------------------
    // This endpoint is permitAll, so these checks are the only guard.
    // -------------------------------------------------------------------------------

    @Test
    void rejectsAnAdminAccessTokenInPlaceOfAChallenge() {
        when(jwtService.getTokenType(CHALLENGE)).thenReturn(TokenType.ADMIN);

        assertThatThrownBy(() -> service.verify(CHALLENGE, currentCode()))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsAnInvalidOrMissingChallenge() {
        when(jwtService.validateToken(CHALLENGE)).thenReturn(false);
        assertThatThrownBy(() -> service.verify(CHALLENGE, "000000")).isInstanceOf(UnauthorizedException.class);
        assertThatThrownBy(() -> service.verify(null, "000000")).isInstanceOf(UnauthorizedException.class);
    }

    /** Without a jti there is nothing to count attempts against, so the cap would not exist. */
    @Test
    void rejectsAChallengeCarryingNoJti() {
        when(jwtService.getTokenId(CHALLENGE)).thenReturn(null);

        assertThatThrownBy(() -> service.verify(CHALLENGE, currentCode()))
            .isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsAChallengeForAnAccountThatIsNoLongerAnAdmin() {
        when(adminUserRepository.findActiveByUserEmail(EMAIL)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.verify(CHALLENGE, currentCode()))
            .isInstanceOf(UnauthorizedException.class);
    }
}
