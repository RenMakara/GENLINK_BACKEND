package kh.edu.acleda.campuslink.modules.admin.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.admin.user.dto.response.AdminUserLoginHistoryResponse;
import kh.edu.acleda.campuslink.modules.auth.entity.RefreshToken;
import kh.edu.acleda.campuslink.modules.auth.entity.SecurityAuditLog;
import kh.edu.acleda.campuslink.modules.auth.repository.RefreshTokenRepository;
import kh.edu.acleda.campuslink.modules.auth.repository.SecurityAuditLogRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.exception.ApiException;
import kh.edu.acleda.campuslink.shared.exception.NotFoundException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

class AdminUserSecurityServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-24T00:00:00Z");

    private UserRepository userRepository;
    private RefreshTokenRepository refreshTokenRepository;
    private SecurityAuditLogRepository securityAuditLogRepository;
    private AdminUserSecurityService service;

    @BeforeEach
    void setUp() {
        userRepository = mock(UserRepository.class);
        refreshTokenRepository = mock(RefreshTokenRepository.class);
        securityAuditLogRepository = mock(SecurityAuditLogRepository.class);
        service = new AdminUserSecurityService(
            userRepository,
            refreshTokenRepository,
            securityAuditLogRepository,
            Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void returnsOnlyTheSafeActiveSessionProjection() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        User user = mock(User.class);
        RefreshToken session = mock(RefreshToken.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(session.getId()).thenReturn(sessionId);
        when(session.getDeviceInfo()).thenReturn("Chrome on Windows");
        when(session.getIpAddress()).thenReturn("203.0.113.10");
        when(session.getUserAgent()).thenReturn("Mozilla/5.0");
        when(session.getCreatedAt()).thenReturn(Instant.parse("2026-09-23T08:00:00Z"));
        when(session.getLastUsedAt()).thenReturn(Instant.parse("2026-09-23T09:00:00Z"));
        when(session.getExpiresAt()).thenReturn(Instant.parse("2026-09-24T08:00:00Z"));
        when(session.getTokenHash()).thenReturn("must-never-leave-api");
        PageRequest pageRequest = PageRequest.of(1, 25);
        when(refreshTokenRepository.findActiveByUserId(userId, NOW, pageRequest))
            .thenReturn(new PageImpl<>(List.of(session), pageRequest, 26));

        var response = service.sessions(userId, 1, 25);

        assertThat(response.page()).isEqualTo(1);
        assertThat(response.size()).isEqualTo(25);
        assertThat(response.totalElements()).isEqualTo(26);
        assertThat(response.content()).hasSize(1);
        assertThat(response.content().get(0).sessionId()).isEqualTo(sessionId);
        assertThat(response.content().get(0).expiresAt())
            .isEqualTo(Instant.parse("2026-09-24T08:00:00Z"));
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);
        assertThat(json).doesNotContain("tokenHash", "must-never-leave-api", "revokedAt");
        verify(refreshTokenRepository).findActiveByUserId(userId, NOW, pageRequest);
    }

    @Test
    void returnsNewestFirstLoginHistoryWithoutAuditDetails() throws Exception {
        UUID userId = UUID.randomUUID();
        UUID auditId = UUID.randomUUID();
        User user = mock(User.class);
        SecurityAuditLog auditLog = mock(SecurityAuditLog.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(auditLog.getId()).thenReturn(auditId);
        when(auditLog.getEventType()).thenReturn("LOGIN_FAILED");
        when(auditLog.getSuccess()).thenReturn(false);
        when(auditLog.getFailureReason()).thenReturn("AUTH_INVALID_CREDENTIALS");
        when(auditLog.getIpAddress()).thenReturn("203.0.113.11");
        when(auditLog.getUserAgent()).thenReturn("Mozilla/5.0");
        when(auditLog.getCreatedAt()).thenReturn(Instant.parse("2026-09-23T10:00:00Z"));
        when(auditLog.getDetails()).thenReturn("sensitive internal details");
        PageRequest pageRequest = PageRequest.of(0, 25);
        when(securityAuditLogRepository.findByUserIdOrderByCreatedAtDesc(userId, pageRequest))
            .thenReturn(new PageImpl<>(List.of(auditLog), pageRequest, 1));

        var response = service.loginHistory(userId, 0, 25);

        AdminUserLoginHistoryResponse entry = response.content().get(0);
        assertThat(entry.auditId()).isEqualTo(auditId);
        assertThat(entry.eventType()).isEqualTo("LOGIN_FAILED");
        assertThat(entry.success()).isFalse();
        assertThat(entry.failureReason()).isEqualTo("AUTH_INVALID_CREDENTIALS");
        assertThat(entry.timestamp()).isEqualTo(Instant.parse("2026-09-23T10:00:00Z"));
        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(response);
        assertThat(json).doesNotContain("details", "sensitive internal details", "email");
        verify(securityAuditLogRepository).findByUserIdOrderByCreatedAtDesc(userId, pageRequest);
    }

    @Test
    void infersSuccessForLegacyRowsThatOnlyHaveTheStatusColumn() {
        UUID userId = UUID.randomUUID();
        User user = mock(User.class);
        SecurityAuditLog auditLog = mock(SecurityAuditLog.class);
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(auditLog.getSuccess()).thenReturn(null);
        when(auditLog.getStatus()).thenReturn("SUCCESS");
        when(securityAuditLogRepository.findByUserIdOrderByCreatedAtDesc(eq(userId), eq(PageRequest.of(0, 25))))
            .thenReturn(new PageImpl<>(List.of(auditLog), PageRequest.of(0, 25), 1));

        assertThat(service.loginHistory(userId, 0, 25).content().get(0).success()).isTrue();
    }

    @Test
    void rejectsUnknownUsersAndInvalidPagination() {
        UUID userId = UUID.randomUUID();
        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.sessions(userId, 0, 25))
            .isInstanceOf(NotFoundException.class);

        when(userRepository.findById(userId)).thenReturn(Optional.of(mock(User.class)));

        assertThatThrownBy(() -> service.loginHistory(userId, -1, 25))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("page must be zero or greater");
        assertThatThrownBy(() -> service.sessions(userId, 0, 24))
            .isInstanceOf(ApiException.class)
            .hasMessageContaining("size must be one of 25, 50, or 100");
    }
}
