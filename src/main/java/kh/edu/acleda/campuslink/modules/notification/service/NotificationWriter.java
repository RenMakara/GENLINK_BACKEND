package kh.edu.acleda.campuslink.modules.notification.service;

import kh.edu.acleda.campuslink.modules.notification.entity.Notification;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import kh.edu.acleda.campuslink.modules.notification.event.NotificationRequestedEvent;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationRepository;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationTemplateRepository;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** Persists a rendered notification in a transaction independent from the source action. */
@Component
public class NotificationWriter {

    private final UserRepository userRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationRepository notificationRepository;
    private final NotificationTemplateRenderer templateRenderer;

    public NotificationWriter(UserRepository userRepository,
                              NotificationTemplateRepository templateRepository,
                              NotificationRepository notificationRepository,
                              NotificationTemplateRenderer templateRenderer) {
        this.userRepository = userRepository;
        this.templateRepository = templateRepository;
        this.notificationRepository = notificationRepository;
        this.templateRenderer = templateRenderer;
    }

    /**
     * Separate bean and transaction boundary are intentional. The listener can call this after
     * the source transaction commits without making notification persistence part of that action.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void writeInNewTransaction(NotificationRequestedEvent event) {
        NotificationRequest request = event.request();
        User recipient = userRepository.findById(request.recipientUserId())
            .orElseThrow(() -> new IllegalStateException(
                "Notification recipient not found: " + request.recipientUserId()));
        if (request.dedupeKey() != null
            && notificationRepository.existsByUserIdAndDedupeKey(
                request.recipientUserId(), request.dedupeKey())) {
            return;
        }
        NotificationTemplate template = templateRepository.findByTemplateKey(request.templateKey())
            .orElseThrow(() -> new IllegalStateException(
                "Notification template not found: " + request.templateKey()));

        Language language = recipient.getPreferredLanguage() == null
            ? Language.ENGLISH : recipient.getPreferredLanguage();
        NotificationTemplateRenderer.RenderedNotification rendered = templateRenderer.render(
            template, language, request.parameters());

        notificationRepository.save(Notification.builder()
            .user(recipient)
            .template(template)
            .title(rendered.title())
            .message(rendered.message())
            .language(language)
            .type(request.type())
            .referenceType(request.referenceType())
            .referenceId(request.referenceId())
            .dedupeKey(request.dedupeKey())
            .readStatus(false)
            .build());
    }
}
