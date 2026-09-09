package kh.edu.acleda.campuslink.modules.community;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.entity.EventRsvp;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.community.repository.EventRsvpRepository;
import kh.edu.acleda.campuslink.modules.community.service.impl.EventReminderServiceImpl;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EventReminderServiceImplTest {

    private static final UUID EVENT_ID = UUID.randomUUID();
    private static final UUID ATTENDEE_ID = UUID.randomUUID();
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 9, 9, 10, 0);

    @Mock private EventRsvpRepository rsvpRepository;
    @Mock private NotificationEmitter notificationEmitter;

    private EventReminderServiceImpl service;
    private Event event;
    private User attendee;
    private EventRsvp rsvp;

    @BeforeEach
    void setUp() {
        service = new EventReminderServiceImpl(rsvpRepository, notificationEmitter);

        attendee = new User();
        attendee.setFullName("Sok Dara");
        ReflectionTestUtils.setField(attendee, "id", ATTENDEE_ID);

        event = Event.builder()
            .title("Career Day")
            .dateTime(NOW.plusDays(1))
            .status(EventStatus.PUBLISHED)
            .build();
        ReflectionTestUtils.setField(event, "id", EVENT_ID);

        rsvp = EventRsvp.builder().event(event).user(attendee).status(RsvpStatus.JOINED).build();
    }

    @Test
    void emitsA24HourReminderForEachJoinedAttendeeInTheWindow() {
        when(rsvpRepository.findForReminder(
            eq(RsvpStatus.JOINED), eq(EventStatus.PUBLISHED),
            eq(NOW.plusHours(24).minusMinutes(5)), eq(NOW.plusHours(24).plusMinutes(5))))
            .thenReturn(List.of(rsvp));

        assertThat(service.sendDueReminders(NOW)).isOne();

        ArgumentCaptor<NotificationRequest> captor = ArgumentCaptor.forClass(NotificationRequest.class);
        verify(notificationEmitter).emit(captor.capture());
        NotificationRequest request = captor.getValue();
        assertThat(request.recipientUserId()).isEqualTo(ATTENDEE_ID);
        assertThat(request.templateKey()).isEqualTo("COMMUNITY_EVENT_REMINDER");
        assertThat(request.type()).isEqualTo("COMMUNITY_EVENT_REMINDER");
        assertThat(request.parameters())
            .containsEntry("eventTitle", "Career Day")
            .containsEntry("startsAt", "10 Sep 2026, 10:00");
        assertThat(request.referenceType()).isEqualTo("EVENT");
        assertThat(request.referenceId()).isEqualTo(EVENT_ID);
        assertThat(request.dedupeKey()).isEqualTo("event-reminder-24h:" + EVENT_ID + ":" + ATTENDEE_ID);
    }

    @Test
    void skipsAnIncompleteRsvpWithoutEmittingANotification() {
        when(rsvpRepository.findForReminder(
            eq(RsvpStatus.JOINED), eq(EventStatus.PUBLISHED), any(), any()))
            .thenReturn(List.of(EventRsvp.builder().event(event).user(null).status(RsvpStatus.JOINED).build()));

        assertThat(service.sendDueReminders(NOW)).isZero();

        verify(notificationEmitter, never()).emit(any(NotificationRequest.class));
    }
}
