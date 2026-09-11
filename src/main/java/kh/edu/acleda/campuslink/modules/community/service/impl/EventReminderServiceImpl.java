package kh.edu.acleda.campuslink.modules.community.service.impl;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.entity.EventRsvp;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;
import kh.edu.acleda.campuslink.modules.community.repository.EventRsvpRepository;
import kh.edu.acleda.campuslink.modules.community.service.EventReminderService;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationEmitter;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Finds attendees whose event is roughly one day away and emits retry-safe in-app reminders.
 * Persistence still happens in Notification's after-commit transaction.
 */
@Slf4j
@Service
public class EventReminderServiceImpl implements EventReminderService {

    static final Duration REMINDER_LEAD = Duration.ofHours(24);
    static final Duration REMINDER_WINDOW = Duration.ofMinutes(5);
    static final String REMINDER_NOTIFICATION_TEMPLATE = "COMMUNITY_EVENT_REMINDER";
    static final String REMINDER_NOTIFICATION_TYPE = "COMMUNITY_EVENT_REMINDER";
    private static final DateTimeFormatter START_TIME_FORMAT =
        DateTimeFormatter.ofPattern("dd MMM yyyy, HH:mm", Locale.ENGLISH);

    private final EventRsvpRepository rsvpRepository;
    private final NotificationEmitter notificationEmitter;

    public EventReminderServiceImpl(EventRsvpRepository rsvpRepository,
                                    NotificationEmitter notificationEmitter) {
        this.rsvpRepository = rsvpRepository;
        this.notificationEmitter = notificationEmitter;
    }

    @Override
    @Transactional(readOnly = true)
    public int sendDueReminders(LocalDateTime now) {
        Objects.requireNonNull(now, "now is required");
        LocalDateTime from = now.plus(REMINDER_LEAD).minus(REMINDER_WINDOW);
        LocalDateTime to = now.plus(REMINDER_LEAD).plus(REMINDER_WINDOW);
        List<EventRsvp> dueRsvps = rsvpRepository.findForReminder(
            RsvpStatus.JOINED, EventStatus.PUBLISHED, from, to);

        int emitted = 0;
        for (EventRsvp rsvp : dueRsvps) {
            try {
                if (emitReminder(rsvp)) {
                    emitted++;
                }
            } catch (RuntimeException ex) {
                Event event = rsvp == null ? null : rsvp.getEvent();
                log.error("Event reminder failed: event={} attendee={}",
                    event == null ? null : event.getId(), attendeeId(rsvp), ex);
            }
        }
        return emitted;
    }

    private boolean emitReminder(EventRsvp rsvp) {
        if (rsvp == null) {
            return false;
        }
        Event event = rsvp.getEvent();
        User attendee = rsvp.getUser();
        if (event == null || event.getId() == null || event.getTitle() == null || event.getTitle().isBlank()
            || event.getDateTime() == null || attendee == null || attendee.getId() == null) {
            log.warn("Skipping event reminder because RSVP details are incomplete: event={} attendee={}",
                event == null ? null : event.getId(), attendeeId(rsvp));
            return false;
        }

        UUID eventId = event.getId();
        UUID attendeeId = attendee.getId();
        notificationEmitter.emit(new NotificationRequest(
            attendeeId,
            REMINDER_NOTIFICATION_TEMPLATE,
            REMINDER_NOTIFICATION_TYPE,
            Map.of(
                "eventTitle", event.getTitle(),
                "startsAt", event.getDateTime().format(START_TIME_FORMAT)),
            "EVENT",
            eventId,
            "event-reminder-24h:" + eventId + ":" + attendeeId));
        return true;
    }

    private UUID attendeeId(EventRsvp rsvp) {
        return rsvp == null || rsvp.getUser() == null ? null : rsvp.getUser().getId();
    }
}
