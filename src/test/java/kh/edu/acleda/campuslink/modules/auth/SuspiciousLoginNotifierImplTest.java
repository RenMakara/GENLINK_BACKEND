package kh.edu.acleda.campuslink.modules.auth;

import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.auth.entity.SecurityAuditLog;
import kh.edu.acleda.campuslink.modules.auth.repository.SecurityAuditLogRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuspiciousLoginNotifierImplTest {

    private static final String EVENT_TYPE = "LOGIN_SUCCESS";
    private static final String KNOWN_IP = "198.51.100.10";
    private static final String KNOWN_USER_AGENT = "GenLink/1 Android";
    private static final UUID USER_ID = UUID.randomUUID();

    @Mock private SecurityAuditLogRepository securityAuditLogRepository;
    @Mock private NotificationEmitter notificationEmitter;

    private SuspiciousLoginNotifierImpl notifier;
    private User user;

    @BeforeEach
    void setUp() {
        notifier = new SuspiciousLoginNotifierImpl(securityAuditLogRepository, notificationEmitter);
        user = new User();
        user.setEmail("user@example.com");
        ReflectionTestUtils.setField(user, "id", USER_ID);
    }

    @Test
    void firstLoginDoesNotCreateAnAlert() {
        when(securityAuditLogRepository
            .findTop50ByUserAndEventTypeAndSuccessTrueOrderByCreatedAtDesc(user, EVENT_TYPE))
            .thenReturn(List.of());

        notifier.notifyIfNeeded(user, KNOWN_IP, KNOWN_USER_AGENT);

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    @Test
    void knownLoginContextDoesNotCreateAnAlert() {
        when(securityAuditLogRepository
            .findTop50ByUserAndEventTypeAndSuccessTrueOrderByCreatedAtDesc(user, EVENT_TYPE))
            .thenReturn(List.of(successfulLogin(KNOWN_IP, KNOWN_USER_AGENT)));

        notifier.notifyIfNeeded(user, KNOWN_IP, KNOWN_USER_AGENT);

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    @Test
    void unknownLoginContextCreatesARecipientScopedAlert() {
        when(securityAuditLogRepository
            .findTop50ByUserAndEventTypeAndSuccessTrueOrderByCreatedAtDesc(user, EVENT_TYPE))
            .thenReturn(List.of(successfulLogin(KNOWN_IP, KNOWN_USER_AGENT)));

        notifier.notifyIfNeeded(user, "203.0.113.25", "GenLink/1 iOS");

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.recipientUserId()).isEqualTo(USER_ID);
        assertThat(request.templateKey()).isEqualTo("SECURITY_SUSPICIOUS_LOGIN");
        assertThat(request.type()).isEqualTo("SECURITY_SUSPICIOUS_LOGIN");
        assertThat(request.parameters()).containsEntry("loginLocation", "IP address 203.0.113.25");
        assertThat(request.referenceType()).isEqualTo("SECURITY");
        assertThat(request.referenceId()).isEqualTo(USER_ID);
        assertThat(request.dedupeKey()).startsWith("suspicious-login:");
    }

    @Test
    void incompleteHistoricalContextDoesNotCreateAFalseAlert() {
        SecurityAuditLog incomplete = successfulLogin(KNOWN_IP, null);
        when(securityAuditLogRepository
            .findTop50ByUserAndEventTypeAndSuccessTrueOrderByCreatedAtDesc(user, EVENT_TYPE))
            .thenReturn(List.of(incomplete));

        notifier.notifyIfNeeded(user, "203.0.113.25", "GenLink/1 iOS");

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }

    @Test
    void auditOrNotificationFailureDoesNotBreakLoginFlow() {
        when(securityAuditLogRepository
            .findTop50ByUserAndEventTypeAndSuccessTrueOrderByCreatedAtDesc(user, EVENT_TYPE))
            .thenThrow(new IllegalStateException("audit store unavailable"));

        assertThatCode(() -> notifier.notifyIfNeeded(user, KNOWN_IP, KNOWN_USER_AGENT))
            .doesNotThrowAnyException();

        doReturn(List.of(successfulLogin("192.0.2.1", "Old client")))
            .when(securityAuditLogRepository)
            .findTop50ByUserAndEventTypeAndSuccessTrueOrderByCreatedAtDesc(user, EVENT_TYPE);
        doThrow(new IllegalStateException("notification unavailable"))
            .when(notificationEmitter).emit(any(NotificationRequest.class));

        assertThatCode(() -> notifier.notifyIfNeeded(user, KNOWN_IP, KNOWN_USER_AGENT))
            .doesNotThrowAnyException();
    }

    private SecurityAuditLog successfulLogin(String ipAddress, String userAgent) {
        SecurityAuditLog entry = new SecurityAuditLog();
        entry.setUser(user);
        entry.setEventType(EVENT_TYPE);
        entry.setSuccess(true);
        entry.setStatus("SUCCESS");
        entry.setIpAddress(ipAddress);
        entry.setUserAgent(userAgent);
        return entry;
    }
}
