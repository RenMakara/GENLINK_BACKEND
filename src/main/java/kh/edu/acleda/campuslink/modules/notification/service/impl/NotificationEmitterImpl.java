package kh.edu.acleda.campuslink.modules.notification.service.impl;

import kh.edu.acleda.campuslink.modules.notification.event.NotificationRequestedEvent;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDeliveryPolicy;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class NotificationEmitterImpl implements NotificationEmitter {

    private final ApplicationEventPublisher eventPublisher;
    private final NotificationDeliveryPolicy deliveryPolicy;

    public NotificationEmitterImpl(ApplicationEventPublisher eventPublisher,
                                   NotificationDeliveryPolicy deliveryPolicy) {
        this.eventPublisher = eventPublisher;
        this.deliveryPolicy = deliveryPolicy;
    }

    @Override
    public void emit(NotificationRequest request) {
        try {
            if (!deliveryPolicy.isAllowed(request)) {
                log.debug("Notification suppressed by recipient preferences recipient={} type={}",
                    request.recipientUserId(), request.type());
                return;
            }
        } catch (RuntimeException exception) {
            // Never let a preference infrastructure problem roll back the source business action.
            log.warn("Could not apply notification delivery policy recipient={} type={}; publishing request",
                request.recipientUserId(), request.type(), exception);
        }
        eventPublisher.publishEvent(new NotificationRequestedEvent(request));
    }
}
