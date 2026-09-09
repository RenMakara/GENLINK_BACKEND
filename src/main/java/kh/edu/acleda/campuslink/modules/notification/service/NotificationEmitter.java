package kh.edu.acleda.campuslink.modules.notification.service;

import java.util.Map;
import java.util.UUID;

/**
 * Small internal port for domain modules that need to request an in-app notification.
 * The implementation publishes an application event. Persistence is deliberately handled by
 * the notification event listener after the source transaction commits.
 */
public interface NotificationEmitter {

    void emit(NotificationRequest request);

    default void emit(UUID recipientUserId,
                      String templateKey,
                      String type,
                      Map<String, String> parameters) {
        emit(new NotificationRequest(recipientUserId, templateKey, type, parameters));
    }

    default void emit(UUID recipientUserId,
                      String templateKey,
                      String type,
                      Map<String, String> parameters,
                      String referenceType,
                      UUID referenceId,
                      String dedupeKey) {
        emit(new NotificationRequest(
            recipientUserId, templateKey, type, parameters, referenceType, referenceId, dedupeKey));
    }
}
