package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.AdminErrorCode;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.admin.user.dto.request.AdminUserLifecycleRequest;
import kh.edu.acleda.campuslink.modules.auth.RefreshTokenService;
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
class AdminUserLifecycleServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-24T02:00:00Z");
    private static final AdminUserLifecycleRequest REASON =
        new AdminUserLifecycleRequest("User requested account recovery");

    @Mock
    private UserRepository userRepository;
    @Mock
    private RefreshTokenService refreshTokenService;
    @Mock
    private AdminAuditService adminAuditService;

    private AdminUserLifecycleService service;

    @BeforeEach
    void setUp() {
        service = new AdminUserLifecycleService(
            userRepository,
            refreshTokenService,
            adminAuditService,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void deletePreservesStatusStampsDeletionRevokesSessionsAndAudits() {
        User user = user(AccountStatus.ACTIVE, 4);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        service.delete(USER_ID, REASON);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.DELETED);
        assertThat(user.getDeletedAt()).isEqualTo(NOW);
        assertThat(user.getStatusBeforeDeletion()).isEqualTo(AccountStatus.ACTIVE);
        assertThat(user.getTokenVersion()).isEqualTo(5);
        verify(refreshTokenService).revoke(user);
        verify(userRepository).save(user);

        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(adminAuditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("USER_DELETE");
        assertThat(audit.getValue().reason()).isEqualTo(REASON.reason());
        assertThat(audit.getValue().targetId()).isEqualTo(USER_ID);
    }

    @Test
    void restoreReturnsTheCapturedPendingStatusInsteadOfPromotingToActive() {
        User user = user(AccountStatus.DELETED, 8);
        user.setDeletedAt(NOW.minusSeconds(3600));
        user.setStatusBeforeDeletion(AccountStatus.PENDING);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        service.restore(USER_ID, REASON);

        assertThat(user.getAccountStatus()).isEqualTo(AccountStatus.PENDING);
        assertThat(user.getDeletedAt()).isNull();
        assertThat(user.getStatusBeforeDeletion()).isNull();
        assertThat(user.getTokenVersion()).isEqualTo(9);
        verify(userRepository).save(user);
        verify(refreshTokenService, never()).revoke(any(User.class));
        verifySuccessfulAudit("USER_RESTORE");
    }

    @Test
    void repeatingDeleteDoesNotRestampOrRevokeAgain() {
        User user = user(AccountStatus.DELETED, 6);
        user.setDeletedAt(NOW.minusSeconds(120));
        user.setStatusBeforeDeletion(AccountStatus.ACTIVE);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        service.delete(USER_ID, REASON);

        assertThat(user.getDeletedAt()).isEqualTo(NOW.minusSeconds(120));
        assertThat(user.getTokenVersion()).isEqualTo(6);
        verify(userRepository, never()).save(any(User.class));
        verify(refreshTokenService, never()).revoke(any(User.class));
        verifySuccessfulAudit("USER_DELETE");
    }

    @Test
    void restoreRejectsAnonymisedRowsWithThePublishedError() {
        User user = user(AccountStatus.DELETED, 10);
        user.setDeletedAt(NOW.minusSeconds(31L * 24 * 3600));
        user.setStatusBeforeDeletion(null);
        when(userRepository.findByIdForUpdate(USER_ID)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> service.restore(USER_ID, REASON))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", AdminErrorCode.USER_ANONYMISED);

        verify(userRepository, never()).save(any(User.class));
        verify(adminAuditService).recordFailure(
            eq("USER_RESTORE"), eq("USER"), eq(USER_ID), eq(REASON.reason()),
            eq(AdminErrorCode.USER_ANONYMISED));
    }

    @Test
    void rejectsAReasonShorterThanThePublishedMinimum() {
        assertThatThrownBy(() -> service.delete(USER_ID, new AdminUserLifecycleRequest("too short")))
            .isInstanceOf(ApiException.class)
            .hasFieldOrPropertyWithValue("errorCode", "VALIDATION_ERROR");
    }

    private void verifySuccessfulAudit(String action) {
        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(adminAuditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo(action);
        assertThat(audit.getValue().targetType()).isEqualTo("USER");
        assertThat(audit.getValue().targetId()).isEqualTo(USER_ID);
    }

    private User user(AccountStatus status, int tokenVersion) {
        User user = new User();
        user.setEmail("target@example.com");
        user.setFullName("Target User");
        user.setUsername("target");
        user.setAccountStatus(status);
        user.setTokenVersion(tokenVersion);
        return user;
    }
}
