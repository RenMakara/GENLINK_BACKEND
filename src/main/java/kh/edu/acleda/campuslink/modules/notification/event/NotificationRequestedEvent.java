package kh.edu.acleda.campuslink.modules.notification.event;

import java.util.Objects;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;

/** Application event raised by a domain module after it has assembled notification facts. */
public record NotificationRequestedEvent(NotificationRequest request) {

    public NotificationRequestedEvent {
        Objects.requireNonNull(request, "request is required");
    }
}
