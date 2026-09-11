package kh.edu.acleda.campuslink.modules.notification.service;

/** A provider failure that should remain retryable by the notification outbox. */
public class MobilePushDeliveryException extends RuntimeException {

    public MobilePushDeliveryException(String message) {
        super(message);
    }

    public MobilePushDeliveryException(String message, Throwable cause) {
        super(message, cause);
    }
}
