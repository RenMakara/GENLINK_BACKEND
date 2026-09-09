package kh.edu.acleda.campuslink.modules.notification.entity;

/** Lifecycle states for a durable in-app notification request. */
public enum NotificationOutboxStatus {
    PENDING,
    PROCESSING,
    DELIVERED,
    SUPPRESSED,
    FAILED
}
