package kh.edu.acleda.campuslink.modules.notification.event;

import kh.edu.acleda.campuslink.modules.notification.service.NotificationWriter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/** Converts notification requests into rows only after the source action has committed. */
@Slf4j
@Component
public class NotificationEventListener {

    private final NotificationWriter notificationWriter;

    public NotificationEventListener(NotificationWriter notificationWriter) {
        this.notificationWriter = notificationWriter;
    }

    @TransactionalEventListener(
        phase = TransactionPhase.AFTER_COMMIT,
        fallbackExecution = true)
    public void onNotificationRequested(NotificationRequestedEvent event) {
        try {
            notificationWriter.writeInNewTransaction(event);
        } catch (RuntimeException exception) {
            // A notification is secondary to the business action that requested it.
            log.error("Could not persist notification recipient={} template={} type={}",
                event.request().recipientUserId(),
                event.request().templateKey(),
                event.request().type(),
                exception);
        }
    }
}
