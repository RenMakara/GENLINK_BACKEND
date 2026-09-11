package kh.edu.acleda.campuslink.modules.notification.service.impl;

import kh.edu.acleda.campuslink.modules.notification.event.NotificationRequestedEvent;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDelivery;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTransport;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationWriter;
import org.springframework.stereotype.Component;

/** Adapter for the persisted in-app notification channel. */
@Component
public class InAppNotificationTransport implements NotificationTransport {

    private final NotificationWriter notificationWriter;

    public InAppNotificationTransport(NotificationWriter notificationWriter) {
        this.notificationWriter = notificationWriter;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.IN_APP;
    }

    @Override
    public void deliver(NotificationDelivery delivery) {
        notificationWriter.writeInNewTransaction(
            new NotificationRequestedEvent(delivery.request()));
    }
}
