package kh.edu.acleda.campuslink.modules.community.service;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.enums.RsvpStatus;

/** Centralizes the authorization rule for exposing an Event's meeting URL. */
public final class EventMeetingUrlAccess {

    private EventMeetingUrlAccess() {
    }

    /**
     * A meeting URL is available only to the organizer or an active RSVP attendee.
     * JOINED is the existing active-attendee status used by EventService and reminders.
     */
    public static boolean canViewMeetingUrl(Event event, UUID viewerId, RsvpStatus rsvpStatus) {
        if (event == null || viewerId == null) {
            return false;
        }
        boolean owner = event.getOrganizer() != null && viewerId.equals(event.getOrganizer().getId());
        return owner || rsvpStatus == RsvpStatus.JOINED;
    }
}
