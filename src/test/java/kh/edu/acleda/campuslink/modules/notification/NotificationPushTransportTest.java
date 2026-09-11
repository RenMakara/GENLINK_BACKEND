package kh.edu.acleda.campuslink.modules.notification;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationTemplateRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDelivery;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTemplateRenderer;
import kh.edu.acleda.campuslink.modules.notification.service.impl.NotificationPushTransport;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationPushTransportTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OUTBOX_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final String TEMPLATE_KEY = "COMMUNITY_EVENT_RSVP_ORGANIZER";

    @Mock private SimpMessagingTemplate messagingTemplate;
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
            EVENT_ID,
            "event-rsvp:1");
        delivery = new NotificationDelivery(OUTBOX_ID, request);
    }

    @Test
    void sendsLocalizedNavigationPayloadToTheAuthenticatedUserQueue() {
        NotificationPushTransport transport = transport(true);
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(templateRepository.findByTemplateKey(TEMPLATE_KEY)).thenReturn(Optional.of(template));

        transport.deliver(delivery);

        ArgumentCaptor<NotificationPushTransport.NotificationPushPayload> captor =
            ArgumentCaptor.forClass(NotificationPushTransport.NotificationPushPayload.class);
        verify(messagingTemplate).convertAndSendToUser(
            eq("student@example.com"),
            eq("/queue/notifications"),
            captor.capture());
        NotificationPushTransport.NotificationPushPayload payload = captor.getValue();
        assertThat(payload.deliveryId()).isEqualTo(OUTBOX_ID);
        assertThat(payload.title()).isEqualTo("ការចុះឈ្មោះថ្មី");
        assertThat(payload.message()).isEqualTo("Sokha បានចុះឈ្មោះសម្រាប់ Career Day");
        assertThat(payload.language()).isEqualTo(Language.KHMER);
        assertThat(payload.type()).isEqualTo("COMMUNITY_EVENT_RSVP");
        assertThat(payload.referenceType()).isEqualTo("EVENT");
        assertThat(payload.referenceId()).isEqualTo(EVENT_ID);
        assertThat(payload.dedupeKey()).isEqualTo("event-rsvp:1");
        assertThat(transport.channel()).isEqualTo(NotificationChannel.PUSH);
    }

    @Test
    void disabledPushTransportDoesNotTouchTheBroker() {
        NotificationPushTransport transport = transport(false);

        assertThat(transport.isEnabled()).isFalse();
        verifyNoInteractions(messagingTemplate, userRepository, templateRepository);
        verify(messagingTemplate, never()).convertAndSendToUser(any(), any(), any());
    }

    private NotificationPushTransport transport(boolean enabled) {
        return new NotificationPushTransport(
            messagingTemplate,
            userRepository,
            templateRepository,
            new NotificationTemplateRenderer(),
            enabled);
    }
}
