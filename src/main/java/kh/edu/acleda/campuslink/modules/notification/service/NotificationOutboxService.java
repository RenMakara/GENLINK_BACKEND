package kh.edu.acleda.campuslink.modules.notification.service;

/** Internal port for durable notification enqueueing and delivery polling. */
public interface NotificationOutboxService {

    /** Stores a notification request without coupling the source business transaction to delivery. */
    void enqueue(NotificationRequest request);

    /** Processes a bounded batch of due rows and returns the number examined. */
    int processPending(int batchSize);
}
