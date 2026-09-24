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
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditEvent;
import kh.edu.acleda.campuslink.modules.admin.audit.AdminAuditService;
import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

class AdminUserSessionRevocationServiceTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID SESSION_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-25T00:00:00Z");

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private AdminAuditService adminAuditService;
    private AdminUserSessionRevocationService service;
    private User user;

    @BeforeEach
    void setUp() {
        userRepository = org.mockito.Mockito.mock(UserRepository.class);
        refreshTokenRepository = org.mockito.Mockito.mock(RefreshTokenRepository.class);
        adminAuditService = org.mockito.Mockito.mock(AdminAuditService.class);
        service = new AdminUserSessionRevocationService(
            userRepository,
            refreshTokenRepository,
            adminAuditService,
            Clock.fixed(NOW, ZoneOffset.UTC));
        user = new User();
        user.setEmail("target@example.com");
        user.setUsername("target");
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
    }

    @Test
    void revokesOnlyTheSelectedSessionAndDoesNotChangeTokenVersion() {
        RefreshToken session = new RefreshToken();
        session.setUser(user);
        when(refreshTokenRepository.findByIdAndUser(SESSION_ID, user))
            .thenReturn(Optional.of(session));

        service.revoke(USER_ID, SESSION_ID);

        assertThat(session.getRevokedAt()).isEqualTo(NOW);
        assertThat(user.getTokenVersion()).isZero();
        verify(refreshTokenRepository).save(session);

        ArgumentCaptor<AdminAuditEvent> audit = ArgumentCaptor.forClass(AdminAuditEvent.class);
        verify(adminAuditService).record(audit.capture());
        assertThat(audit.getValue().action()).isEqualTo("USER_SESSION_REVOKE");
        assertThat(audit.getValue().targetType()).isEqualTo("USER");
        assertThat(audit.getValue().targetId()).isEqualTo(USER_ID);
    }

    @Test
    void treatsAnAlreadyRevokedSessionAsAnIdempotentNoOp() {
        Instant previousRevocation = NOW.minusSeconds(60);
        RefreshToken session = new RefreshToken();
        session.setRevokedAt(previousRevocation);
        when(refreshTokenRepository.findByIdAndUser(SESSION_ID, user))
            .thenReturn(Optional.of(session));

        service.revoke(USER_ID, SESSION_ID);

        assertThat(session.getRevokedAt()).isEqualTo(previousRevocation);
        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(adminAuditService, never()).record(any(AdminAuditEvent.class));
    }

    @Test
    void rejectsASessionOwnedByAnotherUserAsNotFound() {
        when(refreshTokenRepository.findByIdAndUser(eq(SESSION_ID), eq(user)))
            .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(USER_ID, SESSION_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessage("Session not found");

        verify(refreshTokenRepository, never()).save(any(RefreshToken.class));
        verify(adminAuditService).recordFailure(
            "USER_SESSION_REVOKE", "USER", USER_ID, null, "NOT_FOUND");
    }

    @Test
    void rejectsAnUnknownUserAsNotFound() {
        UUID missingUserId = UUID.randomUUID();
        when(userRepository.findById(missingUserId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.revoke(missingUserId, SESSION_ID))
            .isInstanceOf(NotFoundException.class)
            .hasMessageContaining("User not found");

        verify(refreshTokenRepository, never()).findByIdAndUser(any(UUID.class), any(User.class));
        verify(adminAuditService).recordFailure(
            "USER_SESSION_REVOKE", "USER", missingUserId, null, "NOT_FOUND");
    }
}
