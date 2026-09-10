package kh.edu.acleda.campuslink.modules.notification.service.impl;

import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MessagingErrorCode;
import com.google.firebase.messaging.Notification;
import kh.edu.acleda.campuslink.modules.notification.service.MobilePushDeliveryException;
import kh.edu.acleda.campuslink.modules.notification.service.MobilePushPayload;
import kh.edu.acleda.campuslink.modules.notification.service.MobilePushProvider;
import kh.edu.acleda.campuslink.modules.notification.service.MobilePushSendResult;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Firebase Cloud Messaging adapter for Android and Firebase-configured iOS apps. */
@Component
@ConditionalOnProperty(
    name = "app.notification.mobile-push.enabled", havingValue = "true")
public class FirebaseMobilePushProvider implements MobilePushProvider {

    private final FirebaseMessaging firebaseMessaging;

    public FirebaseMobilePushProvider(FirebaseMessaging firebaseMessaging) {
        this.firebaseMessaging = firebaseMessaging;
    }

    @Override
    public MobilePushSendResult send(String deviceToken, MobilePushPayload payload) {
        if (deviceToken == null || deviceToken.isBlank()) {
            throw new MobilePushDeliveryException("Mobile push device token is empty");
        }
        Message.Builder message = Message.builder()
            .setToken(deviceToken)
            .setNotification(Notification.builder()
                .setTitle(payload.title())
                .setBody(payload.body())
                .build());
        payload.data().forEach(message::putData);

        try {
            firebaseMessaging.send(message.build());
            return MobilePushSendResult.success();
        } catch (FirebaseMessagingException exception) {
            // UNREGISTERED is the provider's definitive signal that this installation token is
            // no longer valid. Other failures remain retryable and must not delete a good token.
            if (exception.getMessagingErrorCode() == MessagingErrorCode.UNREGISTERED) {
                return MobilePushSendResult.invalid();
            }
            throw new MobilePushDeliveryException("FCM mobile push delivery failed", exception);
        }
    }
}
