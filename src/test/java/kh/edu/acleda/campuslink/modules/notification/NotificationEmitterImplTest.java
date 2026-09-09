package kh.edu.acleda.campuslink.modules.notification;

import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.event.NotificationRequestedEvent;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.impl.NotificationEmitterImpl;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.context.ApplicationEventPublisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEmitterImplTest {

    @Mock private ApplicationEventPublisher eventPublisher;

    @Test
    void publishesFactsAsAnApplicationEvent() {
        NotificationEmitter emitter = new NotificationEmitterImpl(eventPublisher);
        UUID recipientId = UUID.randomUUID();
        NotificationRequest request = new NotificationRequest(
            recipientId,
            "PRODUCTIVITY_TASK_DUE",
            "PRODUCTIVITY_TASK",
            Map.of("taskTitle", "Review plan"));

        emitter.emit(request);

        ArgumentCaptor<NotificationRequestedEvent> captor =
            ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().request()).isEqualTo(request);
    }
}
