package kh.edu.acleda.campuslink.modules.notification;

import jakarta.mail.internet.MimeMessage;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationTemplateRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDelivery;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTemplateRenderer;
import kh.edu.acleda.campuslink.modules.notification.service.impl.NotificationEmailTransport;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEmailTransportTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OUTBOX_ID = UUID.randomUUID();
    private static final String TEMPLATE_KEY = "COMMUNITY_EVENT_RSVP_ORGANIZER";

    @Mock private JavaMailSender mailSender;
    @Mock private MimeMessage mimeMessage;
    @Mock private UserRepository userRepository;
    @Mock private NotificationTemplateRepository templateRepository;

    private User user;
    private NotificationTemplate template;
    private NotificationDelivery delivery;

    @BeforeEach
    void setUp() {
        user = new User();
        user.setEmail("student@example.com");
        user.setPreferredLanguage(Language.KHMER);
        template = NotificationTemplate.builder()
            .templateKey(TEMPLATE_KEY)
            .titleEn("New registration")
            .titleKm("ការចុះឈ្មោះថ្មី")
            .messageEn("{attendeeName} registered for {eventTitle}.")
            .messageKm("{attendeeName} បានចុះឈ្មោះសម្រាប់ {eventTitle}")
            .build();
        NotificationRequest request = new NotificationRequest(
            USER_ID,
            TEMPLATE_KEY,
            "COMMUNITY_EVENT_RSVP",
            Map.of("attendeeName", "Sokha", "eventTitle", "Career Day"),
            "EVENT",
            UUID.randomUUID(),
            "event-rsvp:1");
        delivery = new NotificationDelivery(OUTBOX_ID, request);
    }

    @Test
    void usesTheGenericNotificationTemplateAndVerifiedSender() {
        NotificationEmailTransport transport = transport(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(templateRepository.findByTemplateKey(TEMPLATE_KEY)).thenReturn(Optional.of(template));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);

        transport.deliver(delivery);

        assertThat(transport.channel()).isEqualTo(NotificationChannel.EMAIL);
        verify(mailSender).createMimeMessage();
        verify(mailSender).send(any(MimeMessage.class));
    }

    @Test
    void disabledEmailTransportDoesNotTouchSmtp() {
        NotificationEmailTransport transport = transport(false);

        assertThatThrownBy(() -> transport.deliver(delivery))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("disabled");

        verifyNoInteractions(mailSender, userRepository, templateRepository);
    }

    @Test
    void sendFailureIsPropagatedForOutboxRetry() {
        NotificationEmailTransport transport = transport(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(templateRepository.findByTemplateKey(TEMPLATE_KEY)).thenReturn(Optional.of(template));
        when(mailSender.createMimeMessage()).thenReturn(mimeMessage);
        doThrow(new RuntimeException("Brevo unavailable"))
            .when(mailSender).send(any(MimeMessage.class));

        assertThatThrownBy(() -> transport.deliver(delivery))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining("Could not send notification email");
    }

    private NotificationEmailTransport transport(boolean enabled) {
        return new NotificationEmailTransport(
            mailSender,
            userRepository,
            templateRepository,
            new NotificationTemplateRenderer(),
            "verified@genlink.kh",
            enabled);
    }
}
