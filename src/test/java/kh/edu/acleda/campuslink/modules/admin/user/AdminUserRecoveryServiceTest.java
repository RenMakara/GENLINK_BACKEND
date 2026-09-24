package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.auth.AuthService;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
import kh.edu.acleda.campuslink.modules.auth.dto.ForgotPasswordRequest;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.AccountStatus;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AdminUserRecoveryServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();

    @Mock
    private UserRepository userRepository;
    @Mock
    private AuthService authService;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminUserRecoveryService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserRecoveryService(
            userRepository, authService, refreshTokenService, adminAuditService);
        lenient().when(adminAuditService.toJson(any())).thenReturn("{}");
    }

    @Test
    void verifiesEmailAndActivatesPendingAccount() {
        User user = user(AccountStatus.PENDING, false, 2);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        service.verifyEmail(USER_ID);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(userRepository).save(user);
        verifySuccessfulAudit("USER_VERIFY_EMAIL");
    }

    @Test
    void verifyingAnAlreadyVerifiedEmailIsAnAuditedNoOp() {
        User user = user(AccountStatus.ACTIVE, true, 4);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        service.verifyEmail(USER_ID);

        assertThat(user.isEmailVerified()).isTrue();
        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.ACTIVE);
        verify(userRepository, never()).save(any(User.class));
        verifySuccessfulAudit("USER_VERIFY_EMAIL");
    }

    @Test
    void resetPasswordDelegatesOnlyTheStoredEmailToTheExistingFlow() {
        User user = user(AccountStatus.ACTIVE, true, 1);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        service.resetPassword(USER_ID);

        ArgumentCaptor<ForgotPasswordRequest> request =
            ArgumentCaptor.forClass(ForgotPasswordRequest.class);
        verify(authService).forgotPassword(request.capture());
        assertThat(request.getValue().email()).isEqualTo("target@example.com");
        verifySuccessfulAudit("USER_RESET_PASSWORD");
    }

    @Test
    void forceLogoutRevokesSessionsAndInvalidatesExistingAccessTokens() {
        User user = user(AccountStatus.ACTIVE, true, 7);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        service.forceLogout(USER_ID);

        assertThat(user.getTokenVersion()).isEqualTo(8);
        verify(refreshTokenService).revoke(user);
        verify(userRepository).save(user);
        verifySuccessfulAudit("USER_FORCE_LOGOUT");
    }

    @Test
    void anonymisedAccountsCannotUseRecoveryActions() {
        User user = user(AccountStatus.DELETED, false, 3);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.verifyEmail(USER_ID))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.USER_ANONYMISED);
        assertThatThrownBy(() -> service.resetPassword(USER_ID))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.USER_ANONYMISED);
        assertThatThrownBy(() -> service.forceLogout(USER_ID))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.USER_ANONYMISED);

        verify(authService, never()).forgotPassword(any(ForgotPasswordRequest.class));
        verify(refreshTokenService, never()).revoke(any(User.class));
        verify(userRepository, never()).save(any(User.class));
        verify(adminAuditService).recordFailure(
            eq("USER_VERIFY_EMAIL"), eq("USER"), eq(USER_ID), eq(null),
            eq(AdminErrorCode.USER_ANONYMISED));
        verify(adminAuditService).recordFailure(
            eq("USER_RESET_PASSWORD"), eq("USER"), eq(USER_ID), eq(null),
            eq(AdminErrorCode.USER_ANONYMISED));
        verify(adminAuditService).recordFailure(
            eq("USER_FORCE_LOGOUT"), eq("USER"), eq(USER_ID), eq(null),
            eq(AdminErrorCode.USER_ANONYMISED));
    }

    private void verifySuccessfulAudit(String action) {
        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(adminAuditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(action);
        assertThat(audit.getValue().targetType()).isEqualTo("USER");
        assertThat(audit.getValue().targetId()).isEqualTo(USER_ID);
    }

    private User user(AccountStatus status, boolean emailVerified, int tokenVersion) {
        User user = new User();
        user.setEmail("target@example.com");
        user.setFullName("Target User");
        user.setUsername("target");
        user.setAccountStatus(status);
        user.setEmailVerified(emailVerified);
        user.setTokenVersion(tokenVersion);
        return user;
    }
}
