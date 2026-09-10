package kh.edu.acleda.campuslink.modules.notification.service;

import java.util.Objects;
import java.util.UUID;

/** One channel-specific delivery attempt for a durable outbox row. */
public record NotificationDelivery(UUID outboxId, NotificationRequest request) {

    public NotificationDelivery {
        Objects.requireNonNull(outboxId, "outboxId is required");
        Objects.requireNonNull(request, "request is required");
    }
}
