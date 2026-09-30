package kh.edu.acleda.campuslink.modules.community.service;

import java.util.UUID;
import kh.edu.acleda.campuslink.modules.community.entity.Event;
import kh.edu.acleda.campuslink.modules.community.enums.EventStatus;

/** Centralizes which Event rows may be exposed to a caller. */
public final class EventVisibility {

    private EventVisibility() {
    }

    /** Published events are public. */
    public static boolean canView(Event event, UUID viewerId) {
        if (event == null || event.getDeletedAt() != null) {
            return false;
        }
        if (event.getStatus() == EventStatus.PUBLISHED) {
            return true;
        }
        return viewerId != null
            && event.getOrganizer() != null
            && viewerId.equals(event.getOrganizer().getId());
    }

    /** Normal attendee registration is available only for published events. */
    public static boolean canRsvp(Event event, UUID viewerId) {
        return canView(event, viewerId) && event.getStatus() == EventStatus.PUBLISHED;
    }
}
