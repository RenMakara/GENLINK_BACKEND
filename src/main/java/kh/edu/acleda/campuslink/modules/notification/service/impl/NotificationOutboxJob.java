package kh.edu.acleda.campuslink.modules.notification.service.impl;

import kh.edu.acleda.campuslink.modules.notification.service.NotificationOutboxService;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/** Polls durable notification requests when scheduling is enabled for this instance. */
@Slf4j
@Component
public class NotificationOutboxJob {

    private final NotificationOutboxService outboxService;
    private final int batchSize;

    public NotificationOutboxJob(
        NotificationOutboxService outboxService,
        @Value("${app.notification.outbox.batch-size:100}") int batchSize) {
        this.outboxService = outboxService;
        this.batchSize = Math.max(1, Math.min(batchSize, 500));
    }

    @Scheduled(
        fixedDelayString = "${app.notification.outbox.poll-ms:5000}",
        initialDelayString = "${app.notification.outbox.initial-delay-ms:5000}")
    public void drain() {
        try {
            int processed = outboxService.processPending(batchSize);
            if (processed > 0) {
                log.debug("Processed {} notification outbox rows", processed);
            }
        } catch (RuntimeException exception) {
            // A scheduler failure must not stop future polls or affect unrelated scheduled jobs.
            log.error("Notification outbox poll failed", exception);
        }
    }
}
