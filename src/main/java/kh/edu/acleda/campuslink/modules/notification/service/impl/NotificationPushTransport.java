package kh.edu.acleda.campuslink.modules.notification.service.impl;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationTemplateRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDelivery;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTemplateRenderer;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTransport;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

/**
 * Online push adapter backed by the authenticated STOMP user queue. It is intentionally not an
 * offline FCM/APNs adapter; REST remains the source of truth when a mobile client is disconnected.
 */
@Slf4j
@Component
public class NotificationPushTransport implements NotificationTransport {

    static final String DESTINATION = "/queue/notifications";

    private final SimpMessagingTemplate messagingTemplate;
    private final UserRepository userRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationTemplateRenderer templateRenderer;
    private final boolean enabled;

    public NotificationPushTransport(
        SimpMessagingTemplate messagingTemplate,
        UserRepository userRepository,
        NotificationTemplateRepository templateRepository,
        NotificationTemplateRenderer templateRenderer,
        @Value("${app.notification.push.enabled:true}") boolean enabled) {
        this.messagingTemplate = messagingTemplate;
        this.userRepository = userRepository;
        this.templateRepository = templateRepository;
        this.templateRenderer = templateRenderer;
        this.enabled = enabled;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.PUSH;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void deliver(NotificationDelivery delivery) {
        if (!enabled) {
            throw new IllegalStateException("Notification push transport is disabled");
        }
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

        messagingTemplate.convertAndSendToUser(
            recipient.getEmail(),
            DESTINATION,
            new NotificationPushPayload(
                delivery.outboxId(),
                rendered.title(),
                rendered.message(),
                language,
                delivery.request().type(),
                delivery.request().referenceType(),
                delivery.request().referenceId(),
                delivery.request().dedupeKey()));
        log.debug("Pushed notification outbox={} type={} to authenticated user queue",
            delivery.outboxId(), delivery.request().type());
    }

    public record NotificationPushPayload(
        UUID deliveryId,
        String title,
        String message,
        Language language,
        String type,
        String referenceType,
        UUID referenceId,
        String dedupeKey) {
    }
}
