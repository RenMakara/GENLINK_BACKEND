package kh.edu.acleda.campuslink.modules.notification.service.impl;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationOutbox;
import kh.edu.acleda.campuslink.modules.notification.entity.NotificationOutboxStatus;
import kh.edu.acleda.campuslink.modules.notification.repository.NotificationOutboxRepository;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationChannel;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDelivery;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationDeliveryPolicy;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationOutboxService;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationRequest;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationTransport;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists notification facts first, then drains them with bounded retries.
 *
 * <p>Channel delivery is deliberately isolated from the source business transaction. If a
 * delivery succeeds and the outbox status update later rolls back, the channel's dedupe guard
 * makes the retry safe where that channel has a durable recipient record.
 */
@Slf4j
@Service
public class NotificationOutboxServiceImpl implements NotificationOutboxService {

    private static final int MAX_BATCH_SIZE = 500;
    private static final List<Duration> RETRY_DELAYS = List.of(
        Duration.ofSeconds(5),
        Duration.ofSeconds(30),
        Duration.ofMinutes(5),
        Duration.ofMinutes(30),
        Duration.ofHours(2));

    private final NotificationOutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;
    private final NotificationDeliveryPolicy deliveryPolicy;
    private final Map<NotificationChannel, NotificationTransport> transports;
    private final Clock clock;
    private final int maxAttempts;

    @Autowired
    public NotificationOutboxServiceImpl(
        NotificationOutboxRepository outboxRepository,
        ObjectMapper objectMapper,
        NotificationDeliveryPolicy deliveryPolicy,
        @Value("${app.notification.outbox.max-attempts:5}") int maxAttempts,
        List<NotificationTransport> transports) {
        this(outboxRepository, objectMapper, deliveryPolicy, transports,
            Clock.systemUTC(), maxAttempts);
    }

    /** Backward-compatible constructor for unit tests that exercise the in-app writer directly. */
    public NotificationOutboxServiceImpl(
        NotificationOutboxRepository outboxRepository,
        ObjectMapper objectMapper,
        NotificationWriter notificationWriter,
        NotificationDeliveryPolicy deliveryPolicy,
        Clock clock,
        int maxAttempts) {
        this(outboxRepository, objectMapper, deliveryPolicy,
            List.of(new InAppNotificationTransport(notificationWriter)), clock, maxAttempts);
    }

    public NotificationOutboxServiceImpl(
        NotificationOutboxRepository outboxRepository,
        ObjectMapper objectMapper,
        NotificationDeliveryPolicy deliveryPolicy,
        List<NotificationTransport> transports,
        Clock clock,
        int maxAttempts) {
        this.outboxRepository = outboxRepository;
        this.objectMapper = objectMapper;
        this.deliveryPolicy = deliveryPolicy;
        this.transports = indexTransports(transports);
        this.clock = clock;
        this.maxAttempts = Math.max(1, maxAttempts);
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(NotificationRequest request) {
        Instant now = clock.instant();
        String parametersJson = serializeParameters(request.parameters());
        for (NotificationChannel channel : request.channels()) {
            NotificationTransport transport = transports.get(channel);
            if (transport == null || !transport.isEnabled()) {
                log.debug("Skipping disabled notification channel recipient={} type={} channel={}",
                    request.recipientUserId(), request.type(), channel);
                continue;
            }
            if (request.dedupeKey() != null
                && outboxRepository.existsByRecipientUserIdAndChannelAndDedupeKey(
                    request.recipientUserId(), channel, request.dedupeKey())) {
                continue;
            }

            outboxRepository.save(NotificationOutbox.builder()
                .recipientUserId(request.recipientUserId())
                .templateKey(request.templateKey())
                .type(request.type())
                .parametersJson(parametersJson)
                .referenceType(request.referenceType())
                .referenceId(request.referenceId())
                .dedupeKey(request.dedupeKey())
                .channel(channel)
                .status(NotificationOutboxStatus.PENDING)
                .attemptCount(0)
                .nextAttemptAt(now)
                .build());
        }
    }

    @Override
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int processPending(int batchSize) {
        if (batchSize <= 0) {
            return 0;
        }

        int boundedBatchSize = Math.min(batchSize, MAX_BATCH_SIZE);
        Instant now = clock.instant();
        List<NotificationOutbox> rows = outboxRepository.findDueForUpdate(
            NotificationOutboxStatus.PENDING,
            now,
            PageRequest.of(0, boundedBatchSize));
        rows.forEach(row -> process(row, now));
        return rows.size();
    }

    private void process(NotificationOutbox outbox, Instant now) {
        outbox.markProcessing();
        NotificationChannel channel = outbox.getChannel() == null
            ? NotificationChannel.IN_APP : outbox.getChannel();
        NotificationTransport transport = transports.get(channel);
        if (transport == null || !transport.isEnabled()) {
            outbox.markSuppressed(now);
            return;
        }

        NotificationRequest request;
        try {
            request = toRequest(outbox);
        } catch (RuntimeException exception) {
            recordFailure(outbox, now, exception);
            return;
        }

        if (!isAllowed(request)) {
            outbox.markSuppressed(now);
            return;
        }

        try {
            transport.deliver(new NotificationDelivery(outbox.getId(), request));
            outbox.markDelivered(now);
        } catch (RuntimeException exception) {
            recordFailure(outbox, now, exception);
        }
    }

    private boolean isAllowed(NotificationRequest request) {
        try {
            return deliveryPolicy.isAllowed(request);
        } catch (RuntimeException exception) {
            // A policy infrastructure failure must not discard a durable notification request.
            log.warn("Could not re-check notification preferences recipient={} type={}; delivering",
                request.recipientUserId(), request.type(), exception);
            return true;
        }
    }

    private void recordFailure(NotificationOutbox outbox, Instant now, RuntimeException exception) {
        Duration retryDelay = retryDelay(outbox.getAttemptCount());
        outbox.recordFailure(now, retryDelay, maxAttempts, errorMessage(exception));
        log.error("Notification outbox delivery failed outbox={} recipient={} attempt={} status={}",
            outbox.getId(), outbox.getRecipientUserId(), outbox.getAttemptCount(), outbox.getStatus(),
            exception);
    }

    private NotificationRequest toRequest(NotificationOutbox outbox) {
        return new NotificationRequest(
            outbox.getRecipientUserId(),
            outbox.getTemplateKey(),
            outbox.getType(),
            deserializeParameters(outbox.getParametersJson()),
            outbox.getReferenceType(),
            outbox.getReferenceId(),
            outbox.getDedupeKey(),
            Set.of(outbox.getChannel() == null ? NotificationChannel.IN_APP : outbox.getChannel()));
    }

    private String serializeParameters(Map<String, String> parameters) {
        try {
            return objectMapper.writeValueAsString(parameters);
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Could not serialize notification parameters", exception);
        }
    }

    private Map<String, String> deserializeParameters(String parametersJson) {
        try {
            return objectMapper.readValue(parametersJson, new TypeReference<Map<String, String>>() { });
        } catch (IOException | RuntimeException exception) {
            throw new IllegalStateException("Could not deserialize notification parameters", exception);
        }
    }

    private static Duration retryDelay(Integer currentAttemptCount) {
        int attemptIndex = currentAttemptCount == null ? 0 : Math.max(0, currentAttemptCount);
        return RETRY_DELAYS.get(Math.min(attemptIndex, RETRY_DELAYS.size() - 1));
    }

    private static String errorMessage(RuntimeException exception) {
        String message = exception.getMessage();
        return message == null || message.isBlank()
            ? exception.getClass().getSimpleName()
            : exception.getClass().getSimpleName() + ": " + message;
    }

    private static Map<NotificationChannel, NotificationTransport> indexTransports(
        List<NotificationTransport> transports) {
        EnumMap<NotificationChannel, NotificationTransport> indexed =
            new EnumMap<>(NotificationChannel.class);
        for (NotificationTransport transport : transports) {
            if (transport == null || transport.channel() == null) {
                throw new IllegalArgumentException("Notification transport and channel are required");
            }
            NotificationTransport previous = indexed.put(transport.channel(), transport);
            if (previous != null) {
                throw new IllegalArgumentException(
                    "Multiple notification transports configured for " + transport.channel());
            }
        }
        return Map.copyOf(indexed);
    }
}
