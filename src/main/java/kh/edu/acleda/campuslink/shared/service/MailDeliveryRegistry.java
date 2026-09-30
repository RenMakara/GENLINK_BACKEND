package kh.edu.acleda.campuslink.shared.service;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import org.springframework.stereotype.Component;

/** Process-local last-outcome view of transactional mail delivery. */
@Component
public class MailDeliveryRegistry {

    private final AtomicReference<Snapshot> snapshot = new AtomicReference<>(
        new Snapshot(Outcome.NEVER_RUN, null));

    public void recordSuccess() {
        record(Outcome.SUCCESS, Instant.now());
    }

    public void recordFailure() {
        record(Outcome.FAILURE, Instant.now());
    }

    public Snapshot snapshot() {
        return snapshot.get();
    }

    void record(Outcome outcome, Instant recordedAt) {
        if (outcome == null || outcome == Outcome.NEVER_RUN) {
            throw new IllegalArgumentException("A delivery record needs a SUCCESS or FAILURE outcome");
        }
        snapshot.set(new Snapshot(outcome, recordedAt));
    }

    public enum Outcome {
        SUCCESS,
        FAILURE,
        NEVER_RUN
    }

    public record Snapshot(Outcome lastOutcome, Instant lastAttemptAt) {
    }
}
