package kh.edu.acleda.campuslink.shared.service;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class MailDeliveryRegistryTest {

    @Test
    void aFreshProcessStartsAtNeverRun() {
        MailDeliveryRegistry.Snapshot snapshot = new MailDeliveryRegistry().snapshot();

        assertThat(snapshot.lastOutcome()).isEqualTo(MailDeliveryRegistry.Outcome.NEVER_RUN);
        assertThat(snapshot.lastAttemptAt()).isNull();
    }

    @Test
    void successAndFailureAreRecordedWithoutRecipientData() {
        MailDeliveryRegistry registry = new MailDeliveryRegistry();
        Instant recordedAt = Instant.parse("2026-09-23T01:02:03Z");

        registry.record(MailDeliveryRegistry.Outcome.SUCCESS, recordedAt);
        assertThat(registry.snapshot().lastOutcome()).isEqualTo(MailDeliveryRegistry.Outcome.SUCCESS);
        assertThat(registry.snapshot().lastAttemptAt()).isEqualTo(recordedAt);

        registry.record(MailDeliveryRegistry.Outcome.FAILURE, recordedAt.plusSeconds(1));
        assertThat(registry.snapshot().lastOutcome()).isEqualTo(MailDeliveryRegistry.Outcome.FAILURE);
    }
}
