package kh.edu.acleda.campuslink.modules.notification.service;

/** Decides whether a notification request is eligible for this recipient right now. */
@FunctionalInterface
public interface NotificationDeliveryPolicy {

    boolean isAllowed(NotificationRequest request);
}
