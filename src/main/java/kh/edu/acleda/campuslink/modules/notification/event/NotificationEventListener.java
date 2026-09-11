package kh.edu.acleda.campuslink.modules.notification.event;

import kh.edu.acleda.campuslink.modules.notification.service.NotificationOutboxService;
import kh.edu.acleda.campuslink.modules.notification.service.NotificationWriter;
import org.springframework.beans.factory.annotation.Autowired;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Converts notification requests into rows only after the source action has committed. */
@Slf4j
@Component
public class NotificationEventListener {

    private final NotificationOutboxService notificationOutboxService;
    private final NotificationWriter legacyNotificationWriter;

    @Autowired
    public NotificationEventListener(NotificationOutboxService notificationOutboxService) {
        this.notificationOutboxService = notificationOutboxService;
        this.legacyNotificationWriter = null;
    }

    /**
     * Compatibility constructor for existing unit tests that exercise the old direct writer
     * boundary. Spring uses the autowired outbox constructor in the application.
     */
    public NotificationEventListener(NotificationWriter notificationWriter) {
        this.notificationOutboxService = null;
        this.legacyNotificationWriter = notificationWriter;
    }

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = true)
    public void onNotificationRequested(NotificationRequestedEvent event) {
        try {
            if (notificationOutboxService != null) {
                notificationOutboxService.enqueue(event.request());
            } else {
                legacyNotificationWriter.writeInNewTransaction(event);
            }
        } catch (RuntimeException exception) {
            // A notification is secondary to the business action that requested it.
            log.error("Could not enqueue notification recipient={} template={} type={}",
                event.request().recipientUserId(),
                event.request().templateKey(),
                event.request().type(),
                exception);
        }
    }
}
