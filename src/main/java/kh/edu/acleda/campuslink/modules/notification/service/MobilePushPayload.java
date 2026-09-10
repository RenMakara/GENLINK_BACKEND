package kh.edu.acleda.campuslink.modules.notification.service;

import java.util.Map;
import java.util.Objects;

/** Provider-neutral payload for one mobile push notification. */
public record MobilePushPayload(
    String title,
    String body,
    Map<String, String> data
) {

    public MobilePushPayload {
        Objects.requireNonNull(title, "title is required");
        Objects.requireNonNull(body, "body is required");
        data = data == null ? Map.of() : Map.copyOf(data);
    }
}
