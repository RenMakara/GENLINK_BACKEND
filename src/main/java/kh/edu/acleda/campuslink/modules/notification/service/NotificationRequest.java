package kh.edu.acleda.campuslink.modules.notification.service;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Internal, non-HTTP request used by domain modules to ask Notification to create an in-app row.
 *
 * <p>Domain modules provide facts and placeholder values. They do not choose the localized
 * title/message or write notification entities directly.
 */
public record NotificationRequest(
    UUID recipientUserId,
    String templateKey,
    String type,
    Map<String, String> parameters
) {

    private static final int MAX_PARAMETER_COUNT = 20;
    private static final int MAX_PARAMETER_KEY_LENGTH = 64;
    private static final int MAX_PARAMETER_VALUE_LENGTH = 1_000;

    public NotificationRequest {
        Objects.requireNonNull(recipientUserId, "recipientUserId is required");
        requireText(templateKey, "templateKey");
        requireText(type, "type");

        Map<String, String> safeParameters = parameters == null ? Map.of() : Map.copyOf(parameters);
        if (safeParameters.size() > MAX_PARAMETER_COUNT) {
            throw new IllegalArgumentException("Too many notification parameters");
        }
        safeParameters.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.length() > MAX_PARAMETER_KEY_LENGTH) {
                throw new IllegalArgumentException("Invalid notification parameter key");
            }
            if (value.length() > MAX_PARAMETER_VALUE_LENGTH) {
                throw new IllegalArgumentException("Notification parameter value is too long");
            }
        });
        parameters = safeParameters;
    }

    private static void requireText(String value, String field) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }
}
