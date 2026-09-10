package kh.edu.acleda.campuslink.modules.notification.entity;

import jakarta.persistence.AttributeOverride;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Table;
import java.time.Duration;
import java.time.Instant;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.shared.entity.BaseEntity;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * Durable, immutable notification facts waiting for a channel transport.
 *
 * <p>The row contains the request data rather than a rendered message. Rendering remains in
 * the channel transport, so a retry uses the same template and recipient language rules as the
 * original request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "notification_outbox")
@AttributeOverride(name = "id", column = @Column(name = "outbox_id", nullable = false, updatable = false))
public class NotificationOutbox extends BaseEntity {

    private static final int MAX_ERROR_LENGTH = 4_000;

    @Column(name = "recipient_user_id", nullable = false)
    private java.util.UUID recipientUserId;

    @Column(name = "template_key", nullable = false, length = 120)
    private String templateKey;

    @Column(name = "type", nullable = false, length = 80)
    private String type;

    @Column(name = "parameters_json", nullable = false, columnDefinition = "TEXT")
    private String parametersJson;

    @Column(name = "reference_type", length = 40)
    private String referenceType;

    @Column(name = "reference_id")
    private java.util.UUID referenceId;

    @Column(name = "dedupe_key", length = 160)
    private String dedupeKey;

    @Enumerated(EnumType.STRING)
    @Column(name = "channel", nullable = false, length = 20)
    @Builder.Default
    private NotificationChannel channel = NotificationChannel.IN_APP;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private NotificationOutboxStatus status;

    @Column(name = "attempt_count", nullable = false)
    private Integer attemptCount;

    @Column(name = "next_attempt_at", nullable = false)
    private Instant nextAttemptAt;

    @Column(name = "last_error", columnDefinition = "TEXT")
    private String lastError;

    @Column(name = "processed_at")
    private Instant processedAt;

    public void markProcessing() {
        status = NotificationOutboxStatus.PROCESSING;
    }

    public void markDelivered(Instant now) {
        status = NotificationOutboxStatus.DELIVERED;
        processedAt = now;
        lastError = null;
    }

    public void markSuppressed(Instant now) {
        status = NotificationOutboxStatus.SUPPRESSED;
        processedAt = now;
        lastError = null;
    }

    public void recordFailure(Instant now, Duration retryDelay, int maxAttempts, String error) {
        int nextAttempt = (attemptCount == null ? 0 : attemptCount) + 1;
        attemptCount = nextAttempt;
        lastError = truncate(error);
        if (nextAttempt >= maxAttempts) {
            status = NotificationOutboxStatus.FAILED;
            processedAt = now;
        } else {
            status = NotificationOutboxStatus.PENDING;
            nextAttemptAt = now.plus(retryDelay);
        }
    }

    private static String truncate(String error) {
        if (error == null) {
            return "Notification delivery failed";
        }
        return error.length() <= MAX_ERROR_LENGTH
            ? error
            : error.substring(0, MAX_ERROR_LENGTH);
    }
}
