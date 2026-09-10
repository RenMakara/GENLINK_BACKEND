package kh.edu.acleda.campuslink.modules.notification.service.impl;

import jakarta.mail.internet.MimeMessage;
import java.nio.charset.StandardCharsets;
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
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * Generic notification email transport. It intentionally does not depend on the auth OTP mail
 * service, because notification email has different retry and failure semantics.
 */
@Slf4j
@Component
public class NotificationEmailTransport implements NotificationTransport {

    private final JavaMailSender mailSender;
    private final UserRepository userRepository;
    private final NotificationTemplateRepository templateRepository;
    private final NotificationTemplateRenderer templateRenderer;
    private final String fromEmail;
    private final boolean enabled;

    public NotificationEmailTransport(
        JavaMailSender mailSender,
        UserRepository userRepository,
        NotificationTemplateRepository templateRepository,
        NotificationTemplateRenderer templateRenderer,
        @Value("${app.mail.from}") String fromEmail,
        @Value("${app.notification.email.enabled:false}") boolean enabled) {
        this.mailSender = mailSender;
        this.userRepository = userRepository;
        this.templateRepository = templateRepository;
        this.templateRenderer = templateRenderer;
        this.fromEmail = fromEmail;
        this.enabled = enabled;
    }

    @Override
    public NotificationChannel channel() {
        return NotificationChannel.EMAIL;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void deliver(NotificationDelivery delivery) {
        if (!enabled) {
            throw new IllegalStateException("Notification email transport is disabled");
        }

        User recipient = recipient(delivery);
        NotificationTemplateRenderer.RenderedNotification rendered = render(delivery, recipient);
        try {
            MimeMessage message = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(
                message, true, StandardCharsets.UTF_8.name());
            helper.setFrom(fromEmail);
            helper.setTo(recipient.getEmail());
            helper.setSubject(rendered.title());
            helper.setText(toHtml(rendered), true);
            message.setHeader("X-GenLink-Notification-Id", delivery.outboxId().toString());
            if (delivery.request().dedupeKey() != null) {
                message.setHeader("X-GenLink-Notification-Key", delivery.request().dedupeKey());
            }
            mailSender.send(message);
            log.debug("Delivered notification email outbox={} type={}",
                delivery.outboxId(), delivery.request().type());
        } catch (Exception exception) {
            // Preserve the exception so the durable outbox applies retry/backoff.
            throw new IllegalStateException("Could not send notification email", exception);
        }
    }

    private NotificationTemplateRenderer.RenderedNotification render(
        NotificationDelivery delivery, User recipient) {
        NotificationTemplate template = templateRepository
            .findByTemplateKey(delivery.request().templateKey())
            .orElseThrow(() -> new IllegalStateException(
                "Notification template not found: " + delivery.request().templateKey()));
        Language language = recipient.getPreferredLanguage() == null
            ? Language.ENGLISH : recipient.getPreferredLanguage();
        return templateRenderer.render(template, language, delivery.request().parameters());
    }

    private User recipient(NotificationDelivery delivery) {
        return userRepository.findById(delivery.request().recipientUserId())
            .orElseThrow(() -> new IllegalStateException(
                "Notification recipient not found: " + delivery.request().recipientUserId()));
    }

    private static String toHtml(NotificationTemplateRenderer.RenderedNotification rendered) {
        return "<!doctype html><html><body style=\"font-family:Arial,sans-serif\">"
            + "<h2>" + escapeHtml(rendered.title()) + "</h2>"
            + "<p>" + escapeHtml(rendered.message()).replace("\n", "<br>") + "</p>"
            + "</body></html>";
    }

    private static String escapeHtml(String value) {
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
