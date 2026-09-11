package kh.edu.acleda.campuslink.modules.notification.service;

/** Adapter boundary for one notification delivery channel. */
public interface NotificationTransport {

    NotificationChannel channel();

    /** Whether this adapter is configured and should receive new outbox rows. */
    default boolean isEnabled() {
        return true;
    }

    /** Delivers one request or throws so the outbox can apply its retry policy. */
    void deliver(NotificationDelivery delivery);
}
