package kh.edu.acleda.campuslink.modules.notification.service.impl;

import kh.edu.acleda.campuslink.modules.notification.event.NotificationRequestedEvent;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDelivery;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTransport;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/** Adapter for the persisted in-app notification channel with online STOMP realtime push. */
@Slf4j
@Component
public class InAppNotificationTransport implements NotificationTransport {

    private final NotificationWriter notificationWriter;
    private final NotificationPushTransport pushTransport;

    @Autowired
    public InAppNotificationTransport(
        NotificationWriter notificationWriter,
        @Autowired(required = false) NotificationPushTransport pushTransport) {
        this.notificationWriter = notificationWriter;
        this.pushTransport = pushTransport;
    }

    public InAppNotificationTransport(NotificationWriter notificationWriter) {
        this(notificationWriter, null);
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void deliver(NotificationDelivery delivery) {
        notificationWriter.writeInNewTransaction(
            new NotificationRequestedEvent(delivery.request()));

        if (pushTransport != null
            && pushTransport.isEnabled()
            && !delivery.request().channels().contains(NotificationChannel.PUSH)) {
            try {
                pushTransport.deliver(delivery);
            } catch (Exception exception) {
                log.warn("Could not dispatch online STOMP push for in-app delivery={}: {}",
                    delivery.outboxId(), exception.getMessage());
            }
        }
    }
}
