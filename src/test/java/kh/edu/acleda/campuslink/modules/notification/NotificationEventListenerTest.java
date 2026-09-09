package kh.edu.acleda.campuslink.modules.notification;

import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.event.NotificationEventListener;
import kh.edu.acleda.campuslink.modules.notification.event.NotificationRequestedEvent;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationWriter;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventListenerTest {

    @Mock private NotificationWriter notificationWriter;

    private NotificationEventListener listener;
    private NotificationRequestedEvent event;

    @BeforeEach
    void setUp() {
        listener = new NotificationEventListener(notificationWriter);
        event = new NotificationRequestedEvent(new NotificationRequest(
            UUID.randomUUID(), "FINANCE_BUDGET_APPROACHING", "FINANCE_BUDGET", Map.of()));
    }

    @Test
    void delegatesToIndependentWriter() {
        listener.onNotificationRequested(event);

        verify(notificationWriter).writeInNewTransaction(event);
    }

    @Test
    void swallowsWriterFailureSoSourceActionIsNotBroken() {
        doThrow(new IllegalStateException("database unavailable"))
            .when(notificationWriter).writeInNewTransaction(event);

        assertThatCode(() -> listener.onNotificationRequested(event))
            .doesNotThrowAnyException();
    }
}
