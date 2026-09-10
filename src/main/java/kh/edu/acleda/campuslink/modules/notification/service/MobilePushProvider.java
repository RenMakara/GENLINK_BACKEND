package kh.edu.acleda.campuslink.modules.notification.service;

/** Provider boundary for OS-level delivery; the outbox does not depend on Firebase classes. */
public interface MobilePushProvider {

    MobilePushSendResult send(String deviceToken, MobilePushPayload payload);
}
