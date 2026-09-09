package kh.edu.acleda.campuslink.modules.notification;

import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.Notification;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import kh.edu.acleda.campuslink.modules.notification.event.NotificationRequestedEvent;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationRepository;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationTemplateRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTemplateRenderer;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationWriter;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationWriterTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final String TEMPLATE_KEY = "COMMUNITY_EVENT_RSVP_ORGANIZER";

    @Mock private UserRepository userRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private NotificationRepository notificationRepository;

    private NotificationWriter writer;
    private User user;
    private NotificationTemplate template;

    @BeforeEach
    void setUp() {
        writer = new NotificationWriter(
            userRepository,
            templateRepository,
            notificationRepository,
            new NotificationTemplateRenderer());
        user = new User();
        ReflectionTestUtils.setField(user, "id", USER_ID);
        user.setPreferredLanguage(Language.KHMER);
        template = NotificationTemplate.builder()
            .templateKey(TEMPLATE_KEY)
            .titleEn("New event registration")
            .titleKm("ការចុះឈ្មោះថ្មី")
            .messageEn("{attendeeName} registered for {eventTitle}.")
            .messageKm("{attendeeName} បានចុះឈ្មោះសម្រាប់ {eventTitle}")
            .build();
    }

    @Test
    void savesRenderedNotificationUsingRecipientLanguage() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(templateRepository.findByTemplateKey(TEMPLATE_KEY)).thenReturn(Optional.of(template));

        NotificationRequestedEvent event = new NotificationRequestedEvent(new NotificationRequest(
            USER_ID,
            TEMPLATE_KEY,
            "COMMUNITY_EVENT_RSVP",
            Map.of("attendeeName", "Sokha", "eventTitle", "Career Day")));

        writer.writeInNewTransaction(event);

        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);
        verify(notificationRepository).save(captor.capture());
        Notification saved = captor.getValue();
        assertThat(saved.getUser()).isSameAs(user);
        assertThat(saved.getTemplate()).isSameAs(template);
        assertThat(saved.getTitle()).isEqualTo("ការចុះឈ្មោះថ្មី");
        assertThat(saved.getMessage()).isEqualTo("Sokha បានចុះឈ្មោះសម្រាប់ Career Day");
        assertThat(saved.getLanguage()).isEqualTo(Language.KHMER);
        assertThat(saved.getType()).isEqualTo("COMMUNITY_EVENT_RSVP");
        assertThat(saved.getReadStatus()).isFalse();
    }

    @Test
    void missingTemplateFailsBeforeSavingBlankNotification() {
        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(templateRepository.findByTemplateKey(TEMPLATE_KEY)).thenReturn(Optional.empty());

        NotificationRequestedEvent event = new NotificationRequestedEvent(new NotificationRequest(
            USER_ID, TEMPLATE_KEY, "COMMUNITY_EVENT_RSVP", Map.of()));

        assertThatThrownBy(() -> writer.writeInNewTransaction(event))
            .isInstanceOf(IllegalStateException.class)
            .hasMessageContaining(TEMPLATE_KEY);
        verify(notificationRepository, never()).save(any());
    }
}
