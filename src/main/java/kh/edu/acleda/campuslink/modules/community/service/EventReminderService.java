package kh.edu.acleda.campuslink.modules.community.service;

import java.time.LocalDateTime;

/** Sends the in-app reminders due for upcoming event attendees. */
public interface EventReminderService {

    /** Emits one reminder per joined attendee for events entering the configured lead-time window. */
    int sendDueReminders(LocalDateTime now);
}
