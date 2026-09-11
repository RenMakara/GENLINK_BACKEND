package kh.edu.acleda.campuslink.modules.notification;

import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationOutbox;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationOutboxStatus;
import kh.edu.acleda.campuslink.modules.notification.event.NotificationRequestedEvent;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationOutboxRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDeliveryPolicy;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTransport;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationWriter;
import kh.edu.acleda.campuslink.modules.notification.service.impl.InAppNotificationTransport;
import kh.edu.acleda.campuslink.modules.notification.service.impl.NotificationOutboxServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationOutboxServiceImplTest {

    private static final UUID USER_ID = UUID.randomUUID();
    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID OUTBOX_ID = UUID.randomUUID();
    private static final Instant NOW = Instant.parse("2026-09-10T00:00:00Z");

    @Mock private NotificationOutboxRepository outboxRepository;
    @Mock private NotificationWriter notificationWriter;
    @Mock private NotificationDeliveryPolicy deliveryPolicy;
    @Mock private NotificationTransport emailTransport;

    private NotificationOutboxServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new NotificationOutboxServiceImpl(
            outboxRepository,
            new ObjectMapper(),
            notificationWriter,
            deliveryPolicy,
            Clock.fixed(NOW, ZoneOffset.UTC),
            3);
    }

    @Test
    void enqueueStoresTheFullRequestAsDurablePendingFacts() {
        NotificationRequest request = request("event-rsvp:1");

        service.enqueue(request);

        ArgumentCaptor<NotificationOutbox> captor = ArgumentCaptor.forClass(NotificationOutbox.class);
        verify(outboxRepository).save(captor.capture());
        NotificationOutbox saved = captor.getValue();
        assertThat(saved.getRecipientUserId()).isEqualTo(USER_ID);
        assertThat(saved.getTemplateKey()).isEqualTo("COMMUNITY_EVENT_RSVP_ORGANIZER");
        assertThat(saved.getType()).isEqualTo("COMMUNITY_EVENT_RSVP");
        assertThat(saved.getParametersJson())
            .contains("attendeeName", "Sokha", "eventTitle", "Career Day");
        assertThat(saved.getReferenceType()).isEqualTo("EVENT");
        assertThat(saved.getReferenceId()).isEqualTo(EVENT_ID);
        assertThat(saved.getDedupeKey()).isEqualTo("event-rsvp:1");
        assertThat(saved.getChannel()).isEqualTo(NotificationChannel.IN_APP);
        assertThat(saved.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(saved.getAttemptCount()).isZero();
        assertThat(saved.getNextAttemptAt()).isEqualTo(NOW);
    }

    @Test
    void enqueueSkipsAnAlreadyQueuedDedupeKey() {
        NotificationRequest request = request("event-rsvp:1");
        when(outboxRepository.existsByRecipientUserIdAndChannelAndDedupeKey(
            USER_ID, NotificationChannel.IN_APP, "event-rsvp:1"))
            .thenReturn(true);

        service.enqueue(request);

        verify(outboxRepository).existsByRecipientUserIdAndChannelAndDedupeKey(
            USER_ID, NotificationChannel.IN_APP, "event-rsvp:1");
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void enqueueCreatesIndependentRowsForEnabledRequestedChannels() {
        when(emailTransport.channel()).thenReturn(NotificationChannel.EMAIL);
        when(emailTransport.isEnabled()).thenReturn(true);
        when(outboxRepository.existsByRecipientUserIdAndChannelAndDedupeKey(
            USER_ID, NotificationChannel.IN_APP, "event-rsvp:1")).thenReturn(false);
        when(outboxRepository.existsByRecipientUserIdAndChannelAndDedupeKey(
            USER_ID, NotificationChannel.EMAIL, "event-rsvp:1")).thenReturn(false);
        NotificationRequest request = new NotificationRequest(
            USER_ID,
            "COMMUNITY_EVENT_RSVP_ORGANIZER",
            "COMMUNITY_EVENT_RSVP",
            Map.of("attendeeName", "Sokha", "eventTitle", "Career Day"),
            "EVENT",
            EVENT_ID,
            "event-rsvp:1",
            java.util.Set.of(NotificationChannel.IN_APP, NotificationChannel.EMAIL));
        NotificationOutboxServiceImpl channelService = new NotificationOutboxServiceImpl(
            outboxRepository,
            new ObjectMapper(),
            deliveryPolicy,
            List.of(new InAppNotificationTransport(notificationWriter), emailTransport),
            Clock.fixed(NOW, ZoneOffset.UTC),
            3);

        channelService.enqueue(request);

        verify(outboxRepository, org.mockito.Mockito.times(2)).save(any(NotificationOutbox.class));
    }

    @Test
    void workerDeliversDueRowsAndMarksThemDelivered() {
        NotificationRequest request = request("event-rsvp:1");
        NotificationOutbox row = pending(request);
        when(outboxRepository.findDueForUpdate(
            NotificationOutboxStatus.PENDING, NOW, PageRequest.of(0, 10)))
            .thenReturn(List.of(row));
        when(deliveryPolicy.isAllowed(request)).thenReturn(true);

        int processed = service.processPending(10);

        assertThat(processed).isOne();
        ArgumentCaptor<NotificationRequestedEvent> captor =
            ArgumentCaptor.forClass(NotificationRequestedEvent.class);
        verify(notificationWriter).writeInNewTransaction(captor.capture());
        assertThat(captor.getValue().request()).isEqualTo(request);
        assertThat(row.getStatus()).isEqualTo(NotificationOutboxStatus.DELIVERED);
        assertThat(row.getAttemptCount()).isZero();
        assertThat(row.getProcessedAt()).isEqualTo(NOW);
        assertThat(row.getLastError()).isNull();
    }

    @Test
    void workerMarksARequestSuppressedWhenPreferencesChangeBeforeDelivery() {
        NotificationRequest request = request("event-rsvp:1");
        NotificationOutbox row = pending(request);
        when(outboxRepository.findDueForUpdate(
            NotificationOutboxStatus.PENDING, NOW, PageRequest.of(0, 10)))
            .thenReturn(List.of(row));
        when(deliveryPolicy.isAllowed(request)).thenReturn(false);

        service.processPending(10);

        verify(notificationWriter, never()).writeInNewTransaction(any());
        assertThat(row.getStatus()).isEqualTo(NotificationOutboxStatus.SUPPRESSED);
        assertThat(row.getProcessedAt()).isEqualTo(NOW);
    }

    @Test
    void workerSchedulesARetryAfterWriterFailure() {
        NotificationRequest request = request("event-rsvp:1");
        NotificationOutbox row = pending(request);
        when(outboxRepository.findDueForUpdate(
            NotificationOutboxStatus.PENDING, NOW, PageRequest.of(0, 10)))
            .thenReturn(List.of(row));
        when(deliveryPolicy.isAllowed(request)).thenReturn(true);
        doThrow(new IllegalStateException("template unavailable"))
            .when(notificationWriter).writeInNewTransaction(any());

        service.processPending(10);

        assertThat(row.getStatus()).isEqualTo(NotificationOutboxStatus.PENDING);
        assertThat(row.getAttemptCount()).isOne();
        assertThat(row.getNextAttemptAt()).isEqualTo(NOW.plusSeconds(5));
        assertThat(row.getLastError()).contains("template unavailable");
    }

    @Test
    void workerStopsRetryingAfterTheConfiguredAttemptLimit() {
        NotificationRequest request = request("event-rsvp:1");
        NotificationOutbox row = pending(request);
        row.setAttemptCount(2);
        when(outboxRepository.findDueForUpdate(
            NotificationOutboxStatus.PENDING, NOW, PageRequest.of(0, 10)))
            .thenReturn(List.of(row));
        when(deliveryPolicy.isAllowed(request)).thenReturn(true);
        doThrow(new IllegalStateException("permanent failure"))
            .when(notificationWriter).writeInNewTransaction(any());

        service.processPending(10);

        assertThat(row.getStatus()).isEqualTo(NotificationOutboxStatus.FAILED);
        assertThat(row.getAttemptCount()).isEqualTo(3);
        assertThat(row.getProcessedAt()).isEqualTo(NOW);
        assertThat(row.getLastError()).contains("permanent failure");
    }

    private static NotificationRequest request(String dedupeKey) {
        return new NotificationRequest(
            USER_ID,
            "COMMUNITY_EVENT_RSVP_ORGANIZER",
            "COMMUNITY_EVENT_RSVP",
            Map.of("attendeeName", "Sokha", "eventTitle", "Career Day"),
            "EVENT",
            EVENT_ID,
            dedupeKey);
    }

    private static NotificationOutbox pending(NotificationRequest request) {
        NotificationOutbox outbox = NotificationOutbox.builder()
            .recipientUserId(request.recipientUserId())
            .templateKey(request.templateKey())
            .type(request.type())
            .parametersJson("{\"attendeeName\":\"Sokha\",\"eventTitle\":\"Career Day\"}")
            .referenceType(request.referenceType())
            .referenceId(request.referenceId())
            .dedupeKey(request.dedupeKey())
            .status(NotificationOutboxStatus.PENDING)
            .attemptCount(0)
            .nextAttemptAt(NOW)
            .build();
        ReflectionTestUtils.setField(outbox, "id", OUTBOX_ID);
        return outbox;
    }
}
