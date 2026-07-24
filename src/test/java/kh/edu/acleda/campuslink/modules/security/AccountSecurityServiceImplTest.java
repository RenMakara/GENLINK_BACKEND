package kh.edu.acleda.campuslink.modules.security;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.auth.PasswordPolicyValidator;
import kh.edu.acleda.campuslink.modules.auth.SecurityAuditService;
import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.auth.repository.SecurityAuditLogRepository;
import kh.edu.acleda.campuslink.modules.auth.security.AuthRateLimitService;
import kh.edu.acleda.campuslink.modules.security.dto.request.ChangePasswordRequest;
import kh.edu.acleda.campuslink.modules.security.dto.request.DeleteAccountRequest;
import kh.edu.acleda.campuslink.modules.security.service.impl.AccountSecurityServiceImpl;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import kh.edu.acleda.campuslink.shared.security.CurrentUserProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
// Client IP is null outside a servlet request context, and anyString() does not match null.
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AccountSecurityServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private UserRepository userRepository;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private SecurityAuditLogRepository securityAuditLogRepository;
    @Mock private SecurityAuditService securityAuditService;
    @Mock private PasswordPolicyValidator passwordPolicyValidator;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private CurrentUserProvider currentUserProvider;
    @Mock private AuthRateLimitService authRateLimitService;

    private AccountSecurityServiceImpl service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new AccountSecurityServiceImpl(userRepository, refreshTokenRepository,
            securityAuditLogRepository, securityAuditService, passwordPolicyValidator,
            passwordEncoder, currentUserProvider, authRateLimitService);

        user = new User();
        user.setEmail("user@example.com");
        user.setPasswordHash("$2a$hash");
        user.setAccountStatus(AccountStatus.ACTIVE);

        when(currentUserProvider.getCurrentUserId()).thenReturn(USER_ID);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void changePasswordEncodesBumpsTokenVersionAndRevokesSessions() {
        when(passwordEncoder.matches("current", "$2a$hash")).thenReturn(true);
        when(passwordEncoder.matches("NewPassword123", "$2a$hash")).thenReturn(false);
        when(passwordEncoder.encode("NewPassword123")).thenReturn("$2a$newhash");

        service.changePassword(new ChangePasswordRequest("current", "NewPassword123"));

        assertThat(user.getPasswordHash()).isEqualTo("$2a$newhash");
        // Revoking refresh tokens alone would leave the current access token usable
        // for up to its 15 minute lifetime, so the version must move too.
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(refreshTokenRepository).revokeByUser(eq(user), any(Instant.class));
        verify(authRateLimitService).clearPasswordChangeFailures(eq("user@example.com"), nullable(String.class));
        verify(securityAuditService).logSuccess(eq("PASSWORD_CHANGE"), eq(user), eq("user@example.com"));
    }

    @Test
    void changePasswordRejectsWrongCurrentPasswordAndRecordsTheAttempt() {
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("wrong", "NewPassword123")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Current password is incorrect");

        // Both must happen, otherwise the endpoint is a silent brute-force oracle.
        verify(authRateLimitService).recordPasswordChangeFailure(eq("user@example.com"), nullable(String.class));
        verify(securityAuditService).logFailure(eq("PASSWORD_CHANGE"), eq(user), eq("user@example.com"), nullable(String.class));
        verify(userRepository, never()).save(any(User.class));
        assertThat(user.getTokenVersion()).isZero();
    }

    @Test
    void changePasswordIsBlockedByTheRateLimiterBeforeTheHashIsEvenChecked() {
        doThrowOnRateLimit();

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("current", "NewPassword123")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Too many");

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void changePasswordRejectsAccountWithNoPasswordSet() {
        user.setPasswordHash(null);

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("current", "NewPassword123")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("no password set");
    }

    @Test
    void changePasswordRejectsReusingTheCurrentPassword() {
        when(passwordEncoder.matches("current", "$2a$hash")).thenReturn(true);
        when(passwordEncoder.matches("current", "$2a$hash")).thenReturn(true);

        assertThatThrownBy(() -> service.changePassword(new ChangePasswordRequest("current", "current")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("must be different");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void revokeAllSessionsBumpsTokenVersionSoAccessTokensDieImmediately() {
        service.revokeAllSessions();

        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(refreshTokenRepository).revokeByUser(eq(user), any(Instant.class));
        verify(userRepository).save(user);
    }

    @Test
    void revokeSessionMarksOnlyThatSessionRevoked() {
        UUID sessionId = UUID.randomUUID();
        RefreshToken token = new RefreshToken();
        when(refreshTokenRepository.findByIdAndUser(sessionId, user)).thenReturn(Optional.of(token));

        service.revokeSession(sessionId);

        assertThat(token.getRevokedAt()).isNotNull();
        // Per-user versioning would sign every device out, so it must not move here.
        assertThat(user.getTokenVersion()).isZero();
    }

    @Test
    void revokeSessionIsIdempotentForAnAlreadyRevokedSession() {
        UUID sessionId = UUID.randomUUID();
        Instant revokedAt = Instant.now().minusSeconds(60);
        RefreshToken token = new RefreshToken();
        token.setRevokedAt(revokedAt);
        when(refreshTokenRepository.findByIdAndUser(sessionId, user)).thenReturn(Optional.of(token));

        service.revokeSession(sessionId);

        assertThat(token.getRevokedAt()).isEqualTo(revokedAt);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
    }

    @Test
    void revokeSessionRejectsSomeoneElsesSessionAsNotFound() {
        UUID sessionId = UUID.randomUUID();
        when(refreshTokenRepository.findByIdAndUser(sessionId, user)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revokeSession(sessionId))
            .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getActiveSessionsNeverExposesTheTokenHash() {
        RefreshToken token = new RefreshToken();
        token.setTokenHash("super-secret-hash");
        token.setDeviceInfo("Pixel 8");
        token.setIpAddress("203.0.113.7");
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        when(refreshTokenRepository
            .findByUserAndRevokedAtIsNullAndExpiresAtAfterOrderByCreatedAtDesc(eq(user), any(Instant.class)))
            .thenReturn(List.of(token));

        var sessions = service.getActiveSessions();

        assertThat(sessions).hasSize(1);
        assertThat(sessions.get(0).deviceInfo()).isEqualTo("Pixel 8");
        assertThat(sessions.toString()).doesNotContain("super-secret-hash");
    }

    @Test
    void deleteCurrentAccountSoftDeletesAndCutsOffEveryCredential() {
        when(passwordEncoder.matches("current", "$2a$hash")).thenReturn(true);

        service.deleteCurrentAccount(new DeleteAccountRequest("current"));

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(user.getDeletedAt()).isNotNull();
        // The row stays readable, so the live access token has to be invalidated too.
        assertThat(user.getTokenVersion()).isEqualTo(1);
        verify(userRepository).save(user);
        verify(refreshTokenRepository).revokeByUser(eq(user), any(Instant.class));
        verify(securityAuditService).logSuccess(eq("ACCOUNT_DELETE"), eq(user), eq("user@example.com"));
    }

    @Test
    void deleteCurrentAccountRejectsWrongPasswordAndRecordsTheAttempt() {
        when(passwordEncoder.matches("wrong", "$2a$hash")).thenReturn(false);

        assertThatThrownBy(() -> service.deleteCurrentAccount(new DeleteAccountRequest("wrong")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Password is incorrect");

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getDeletedAt()).isNull();
        verify(authRateLimitService).recordAccountDeleteFailure(eq("user@example.com"), nullable(String.class));
        verify(securityAuditService).logFailure(eq("ACCOUNT_DELETE"), eq(user), eq("user@example.com"), nullable(String.class));
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deleteCurrentAccountRequiresAPasswordWhenTheAccountHasOne() {
        assertThatThrownBy(() -> service.deleteCurrentAccount(new DeleteAccountRequest("  ")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Password is required");

        assertThatThrownBy(() -> service.deleteCurrentAccount(null))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Password is required");

        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void deleteCurrentAccountAllowsSocialOnlyAccountsWithNoBody() {
        // Nothing to confirm against, so possession of the access token has to be enough.
        user.setPasswordHash(null);

        service.deleteCurrentAccount(null);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(user.getDeletedAt()).isNotNull();
        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void deleteCurrentAccountIsBlockedByTheRateLimiterBeforeTheHashIsEvenChecked() {
        org.mockito.Mockito.doThrow(new ApiException("Too many account deletion attempts. Please try again later.",
                "AUTH_TOO_MANY_ATTEMPTS"))
            .when(authRateLimitService).checkAccountDeleteAllowed(nullable(String.class), nullable(String.class));

        assertThatThrownBy(() -> service.deleteCurrentAccount(new DeleteAccountRequest("current")))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("Too many");

        verify(passwordEncoder, never()).matches(any(), any());
    }

    @Test
    void deleteCurrentAccountIsNotFoundForAnAlreadyDeletedAccount() {
        user.setAccountStatus(AccountStatus.DELETED);
        user.setDeletedAt(Instant.now().minusSeconds(60));

        assertThatThrownBy(() -> service.deleteCurrentAccount(new DeleteAccountRequest("current")))
            .isInstanceOf(NotFoundException.class);

        verify(userRepository, never()).save(any(User.class));
    }

    private void doThrowOnRateLimit() {
        org.mockito.Mockito.doThrow(new ApiException("Too many password change attempts. Please try again later.",
                "AUTH_TOO_MANY_ATTEMPTS"))
            .when(authRateLimitService).checkPasswordChangeAllowed(nullable(String.class), nullable(String.class));
    }
}
