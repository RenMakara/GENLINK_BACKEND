package kh.edu.acleda.campuslink.modules.notification.service;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** Small internal port for domain modules that need to request a notification. */
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

    default void emit(UUID recipientUserId,
                      String templateKey,
                      String type,
                      Map<String, String> parameters,
                      String referenceType,
                      UUID referenceId,
                      String dedupeKey,
                      Set<NotificationChannel> channels) {
        emit(new NotificationRequest(
            recipientUserId, templateKey, type, parameters, referenceType, referenceId, dedupeKey,
            channels));
    }
}
