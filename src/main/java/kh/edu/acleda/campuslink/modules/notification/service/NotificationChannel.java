package kh.edu.acleda.campuslink.modules.notification.service;

/** Delivery channels supported by the notification application port. */
public enum NotificationChannel {
    IN_APP,
    EMAIL,
    PUSH,
    /** OS-level mobile push delivered through FCM while the app is backgrounded or closed. */
    MOBILE_PUSH
}
