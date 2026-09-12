package kh.edu.acleda.campuslink.modules.notification.service.impl;

import java.time.Clock;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationDevice;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationDeviceRepository;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationTemplateRepository;
import kh.edu.acleda.campuslink.modules.notification.service.MobilePushDeliveryException;
import kh.edu.acleda.campuslink.modules.notification.service.MobilePushPayload;
import kh.edu.acleda.campuslink.modules.notification.service.MobilePushProvider;
import kh.edu.acleda.campuslink.modules.notification.service.MobilePushSendResult;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDelivery;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTemplateRenderer;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTransport;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.crypto.FieldEncryptor;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/** Fan-out transport for offline mobile notifications registered on a user's installations. */
@Slf4j
@Component
@ConditionalOnProperty(
    name = "app.notification.mobile-push.enabled", havingValue = "true")
public class NotificationMobilePushTransport implements NotificationTransport {

    private final NotificationDeviceRepository deviceRepository;
    private final UserRepository userRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationTemplateRenderer templateRenderer;
    private final FieldEncryptor fieldEncryptor;
    private final MobilePushProvider provider;
    private final Clock clock;

    @Autowired
    public NotificationMobilePushTransport(
        NotificationDeviceRepository deviceRepository,
        UserRepository userRepository,
        NotificationTemplateRepository templateRepository,
        NotificationTemplateRenderer templateRenderer,
        FieldEncryptor fieldEncryptor,
        MobilePushProvider provider) {
        this(deviceRepository, userRepository, templateRepository, templateRenderer,
            fieldEncryptor, provider, Clock.systemUTC());
    }

    public NotificationMobilePushTransport(
        NotificationDeviceRepository deviceRepository,
        UserRepository userRepository,
        NotificationTemplateRepository templateRepository,
        NotificationTemplateRenderer templateRenderer,
        FieldEncryptor fieldEncryptor,
        MobilePushProvider provider,
        Clock clock) {
        this.deviceRepository = deviceRepository;
        this.userRepository = userRepository;
        this.templateRepository = templateRepository;
        this.templateRenderer = templateRenderer;
        this.fieldEncryptor = fieldEncryptor;
        this.provider = provider;
        this.clock = clock;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.MOBILE_PUSH;
    }

    @Override
    public void deliver(NotificationDelivery delivery) {
        User recipient = userRepository.findById(delivery.request().recipientUserId())
            .orElseThrow(() -> new IllegalStateException(
                "Notification recipient not found: " + delivery.request().recipientUserId()));
        NotificationTemplate template = templateRepository
            .findByTemplateKey(delivery.request().templateKey())
            .orElseThrow(() -> new IllegalStateException(
                "Notification template not found: " + delivery.request().templateKey()));
        Language language = recipient.getPreferredLanguage() == null
            ? Language.ENGLISH : recipient.getPreferredLanguage();
        NotificationTemplateRenderer.RenderedNotification rendered = templateRenderer.render(
            template, language, delivery.request().parameters());

        List<NotificationDevice> devices = deviceRepository
            .findByUserIdAndActiveTrueOrderByLastSeenAtDesc(
                delivery.request().recipientUserId());
        if (devices.isEmpty()) {
            // There is nothing to retry in this case. The in-app notification remains the source
            // of truth, and a later device registration will receive future notifications.
            log.debug("No active mobile push devices recipient={} type={}",
                delivery.request().recipientUserId(), delivery.request().type());
            return;
        }

        MobilePushPayload payload = new MobilePushPayload(
            rendered.title(), rendered.message(), payloadData(delivery));
        RuntimeException retryableFailure = null;
        int deliveredCount = 0;
        int invalidCount = 0;
        for (NotificationDevice device : devices) {
            String token;
            try {
                token = fieldEncryptor.decrypt(device.getTokenCiphertext());
            } catch (RuntimeException exception) {
                // A decryption error is a server key/ciphertext problem, not proof that the
                // provider rejected this token. Keep the device and retry after configuration or
                // data repair; deactivating every row after a key rotation would be destructive.
                retryableFailure = retryableFailure == null
                    ? new MobilePushDeliveryException("Could not decrypt mobile push token", exception)
                    : retryableFailure;
                continue;
            }

            try {
                MobilePushSendResult result = provider.send(token, payload);
                if (result == null) {
                    throw new MobilePushDeliveryException("Mobile push provider returned no result");
                }
                if (result.invalidToken()) {
                    deactivate(device);
                    invalidCount++;
                } else if (result.delivered()) {
                    deliveredCount++;
                }
            } catch (RuntimeException exception) {
                // Keep processing other devices. If any provider failure remains, the durable
                // outbox retries the row; successful devices may see an at-least-once duplicate.
                retryableFailure = retryableFailure == null
                    ? exception : retryableFailure;
            }
        }

        if (retryableFailure != null) {
            throw retryableFailure;
        }
        log.debug("Delivered mobile notification outbox={} recipient={} devices={} invalid={}",
            delivery.outboxId(), delivery.request().recipientUserId(), deliveredCount, invalidCount);
    }

    private void deactivate(NotificationDevice device) {
        device.setActive(false);
        device.setLastFailureAt(clock.instant());
        deviceRepository.save(device);
    }

    private static Map<String, String> payloadData(NotificationDelivery delivery) {
        Map<String, String> data = new HashMap<>();
        data.put("deliveryId", delivery.outboxId().toString());
        data.put("type", delivery.request().type());
        if (delivery.request().referenceType() != null) {
            data.put("referenceType", delivery.request().referenceType());
        }
        if (delivery.request().referenceId() != null) {
            data.put("referenceId", delivery.request().referenceId().toString());
        }
        if (delivery.request().dedupeKey() != null) {
            data.put("dedupeKey", delivery.request().dedupeKey());
        }
        return Map.copyOf(data);
    }
}
