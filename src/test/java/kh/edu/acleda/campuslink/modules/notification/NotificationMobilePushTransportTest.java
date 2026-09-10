package kh.edu.acleda.campuslink.modules.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationDevice;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationDevicePlatform;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationTemplate;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationDeviceRepository;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationTemplateRepository;
import kh.edu.acleda.campuslink.modules.notification.service.MobilePushDeliveryException;
import kh.edu.acleda.campuslink.modules.notification.service.MobilePushPayload;
import kh.edu.acleda.campuslink.modules.notification.service.MobilePushProvider;
import kh.edu.acleda.campuslink.modules.notification.service.MobilePushSendResult;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDelivery;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTemplateRenderer;
import kh.edu.acleda.campuslink.modules.notification.service.impl.NotificationMobilePushTransport;
import kh.edu.acleda.campuslink.modules.user.UserRepository;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import kh.edu.acleda.campuslink.shared.crypto.FieldEncryptor;
import kh.edu.acleda.campuslink.shared.enums.Language;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class NotificationMobilePushTransportTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID OUTBOX_ID = UUID.randomUUID();
    private static final UUID REFERENCE_ID = UUID.randomUUID();
    private static final String TOKEN = "fcm-token-123";
    private static final String CIPHERTEXT = "v1:encrypted-token";
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");

    @Mock private NotificationDeviceRepository deviceRepository;
    @Mock private UserRepository userRepository;
    @Mock private NotificationTemplateRepository templateRepository;
    @Mock private NotificationTemplateRenderer templateRenderer;
    @Mock private FieldEncryptor fieldEncryptor;
    @Mock private MobilePushProvider provider;
    @Mock private User user;
    @Mock private NotificationTemplate template;

    private NotificationMobilePushTransport transport;
    private NotificationDelivery delivery;
    private NotificationDevice device;

    @BeforeEach
    void setUp() {
        transport = new NotificationMobilePushTransport(
            deviceRepository,
            userRepository,
            templateRepository,
            templateRenderer,
            fieldEncryptor,
            provider,
            Clock.fixed(NOW, ZoneOffset.UTC));
        NotificationRequest request = new NotificationRequest(
            USER_ID,
            "TEST_TEMPLATE",
            "COMMUNITY_EVENT_RSVP",
            Map.of("eventName", "Campus meetup"),
            "EVENT",
            REFERENCE_ID,
            "event-rsvp:" + REFERENCE_ID,
            Set.of(NotificationChannel.MOBILE_PUSH));
        delivery = new NotificationDelivery(OUTBOX_ID, request);
        device = NotificationDevice.builder()
            .platform(NotificationDevicePlatform.ANDROID)
            .tokenCiphertext(CIPHERTEXT)
            .active(true)
            .build();

        when(userRepository.findById(USER_ID)).thenReturn(Optional.of(user));
        when(user.getPreferredLanguage()).thenReturn(Language.ENGLISH);
        when(templateRepository.findByTemplateKey("TEST_TEMPLATE"))
            .thenReturn(Optional.of(template));
        when(templateRenderer.render(
            template, Language.ENGLISH, delivery.request().parameters()))
            .thenReturn(new NotificationTemplateRenderer.RenderedNotification(
                "Event reminder", "Campus meetup is starting soon"));
        when(deviceRepository.findByUserIdAndActiveTrueOrderByLastSeenAtDesc(USER_ID))
            .thenReturn(java.util.List.of(device));
        when(fieldEncryptor.decrypt(CIPHERTEXT)).thenReturn(TOKEN);
    }

    @Test
    void sendsOnePayloadToEachActiveDevice() {
        when(provider.send(eq(TOKEN), any(MobilePushPayload.class)))
            .thenReturn(MobilePushSendResult.success());

        transport.deliver(delivery);

        ArgumentCaptor<MobilePushPayload> payload = ArgumentCaptor.forClass(MobilePushPayload.class);
        verify(provider).send(eq(TOKEN), payload.capture());
        assertThat(payload.getValue().title()).isEqualTo("Event reminder");
        assertThat(payload.getValue().body()).contains("Campus meetup");
        assertThat(payload.getValue().data())
            .containsEntry("type", "COMMUNITY_EVENT_RSVP")
            .containsEntry("referenceType", "EVENT")
            .containsEntry("referenceId", REFERENCE_ID.toString())
            .containsEntry("deliveryId", OUTBOX_ID.toString());
    }

    @Test
    void deactivatesAnUnregisteredTokenWithoutRetryingTheOutbox() {
        when(provider.send(eq(TOKEN), any(MobilePushPayload.class)))
            .thenReturn(MobilePushSendResult.invalid());

        transport.deliver(delivery);

        assertThat(device.getActive()).isFalse();
        assertThat(device.getLastFailureAt()).isEqualTo(NOW);
        verify(deviceRepository).save(device);
    }

    @Test
    void propagatesTransientProviderFailureForOutboxRetry() {
        when(provider.send(eq(TOKEN), any(MobilePushPayload.class)))
            .thenThrow(new MobilePushDeliveryException("FCM unavailable"));

        assertThatThrownBy(() -> transport.deliver(delivery))
            .isInstanceOf(MobilePushDeliveryException.class)
            .hasMessage("FCM unavailable");
    }
}
