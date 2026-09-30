package kh.edu.acleda.campuslink.modules.notification.service;

import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

/** Internal, non-HTTP request used by domain modules to request one or more notification channels. */
public record NotificationRequest(
    UUID recipientUserId,
    String templateKey,
    String type,
    Map<String, String> parameters,
    String referenceType,
    UUID referenceId,
    String dedupeKey,
    Set<NotificationChannel> channels
) {

    private static final int MAX_PARAMETER_COUNT = 20;
    private static final int MAX_PARAMETER_KEY_LENGTH = 64;
    private static final int MAX_PARAMETER_VALUE_LENGTH = 1_000;
    private static final int MAX_TEMPLATE_KEY_LENGTH = 120;
    private static final int MAX_TYPE_LENGTH = 80;
    private static final int MAX_REFERENCE_TYPE_LENGTH = 40;
    private static final int MAX_DEDUPE_KEY_LENGTH = 160;

    public NotificationRequest(UUID recipientUserId,
                               String templateKey,
                               String type,
                               Map<String, String> parameters) {
        this(recipientUserId, templateKey, type, parameters, null, null, null,
            Set.of(NotificationChannel.IN_APP));
    }

    public NotificationRequest(UUID recipientUserId,
                               String templateKey,
                               String type,
                               Map<String, String> parameters,
                               String referenceType,
                               UUID referenceId,
                               String dedupeKey) {
        this(recipientUserId, templateKey, type, parameters, referenceType, referenceId, dedupeKey,
            Set.of(NotificationChannel.IN_APP));
    }

    public NotificationRequest {
        Objects.requireNonNull(recipientUserId, "recipientUserId is required");
        requireText(templateKey, "templateKey", MAX_TEMPLATE_KEY_LENGTH);
        requireText(type, "type", MAX_TYPE_LENGTH);
        requireOptionalText(referenceType, "referenceType", MAX_REFERENCE_TYPE_LENGTH);
        requireOptionalText(dedupeKey, "dedupeKey", MAX_DEDUPE_KEY_LENGTH);
        if ((referenceType == null) != (referenceId == null)) {
            throw new IllegalArgumentException("referenceType and referenceId must be supplied together");
        }

        Map<String, String> rawParameters = parameters == null ? Map.of() : parameters;
        Map<String, String> safeParameters = rawParameters;
        if (safeParameters.size() > MAX_PARAMETER_COUNT) {
            throw new IllegalArgumentException("Too many notification parameters");
        }
        safeParameters.forEach((key, value) -> {
            if (key == null || key.isBlank() || key.length() > MAX_PARAMETER_KEY_LENGTH) {
                throw new IllegalArgumentException("Invalid notification parameter key");
            }
            if (value == null) {
                throw new IllegalArgumentException("Notification parameter is null: " + key);
            }
            if (value.length() > MAX_PARAMETER_VALUE_LENGTH) {
                throw new IllegalArgumentException("Notification parameter value is too long");
            }
        });
        parameters = Map.copyOf(safeParameters);

        Set<NotificationChannel> safeChannels = channels == null
            ? Set.of(NotificationChannel.IN_APP)
            : Set.copyOf(channels);
        if (safeChannels.isEmpty()) {
            throw new IllegalArgumentException("At least one notification channel is required");
        }
        channels = safeChannels;
    }

    private static void requireText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
    }

    private static void requireOptionalText(String value, String field, int maxLength) {
        if (value == null) {
            return;
        }
        if (value.isBlank()) {
            throw new IllegalArgumentException(field + " must not be blank");
        }
        if (value.length() > maxLength) {
            throw new IllegalArgumentException(field + " is too long");
        }
    }
}
