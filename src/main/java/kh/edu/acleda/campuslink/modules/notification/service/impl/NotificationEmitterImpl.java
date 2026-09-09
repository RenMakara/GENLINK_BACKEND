package kh.edu.acleda.campuslink.modules.notification.service.impl;

import kh.edu.acleda.campuslink.modules.notification.event.NotificationRequestedEvent;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

@Service
public class NotificationEmitterImpl implements NotificationEmitter {

    private final ApplicationEventPublisher eventPublisher;

    public NotificationEmitterImpl(ApplicationEventPublisher eventPublisher) {
        this.eventPublisher = eventPublisher;
    }

    @Override
    public void emit(NotificationRequest request) {
        eventPublisher.publishEvent(new NotificationRequestedEvent(request));
    }
}
