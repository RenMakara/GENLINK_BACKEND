package kh.edu.acleda.campuslink.shared.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import org.junit.jupiter.api.Test;

class ScheduledJobRunRegistryTest {

    @Test
    void allRequiredJobsStartAsNeverRun() {
        ScheduledJobRunRegistry registry = new ScheduledJobRunRegistry();

        assertThat(registry.snapshot()).extracting(ScheduledJobRunRegistry.JobRun::name)
            .containsExactly(
                ScheduledJobRunRegistry.ACCOUNT_ANONYMIZATION_JOB,
                ScheduledJobRunRegistry.RECURRING_FINANCE_JOB,
                ScheduledJobRunRegistry.MEDIA_CLEANUP_JOB);
        assertThat(registry.snapshot()).allSatisfy(job -> {
            assertThat(job.lastRun()).isNull();
            assertThat(job.lastOutcome()).isEqualTo(ScheduledJobRunRegistry.Outcome.NEVER_RUN);
        });
    }

    @Test
    void multipleJobsKeepIndependentOutcomes() {
        ScheduledJobRunRegistry registry = new ScheduledJobRunRegistry();
        Instant now = Instant.parse("2026-09-23T01:02:03Z");

        registry.record(ScheduledJobRunRegistry.ACCOUNT_ANONYMIZATION_JOB,
            ScheduledJobRunRegistry.Outcome.SUCCESS, now);
        registry.record(ScheduledJobRunRegistry.MEDIA_CLEANUP_JOB,
            ScheduledJobRunRegistry.Outcome.FAILURE, now.plusSeconds(1));

        assertThat(registry.stateFor(ScheduledJobRunRegistry.ACCOUNT_ANONYMIZATION_JOB).lastOutcome())
            .isEqualTo(ScheduledJobRunRegistry.Outcome.SUCCESS);
        assertThat(registry.stateFor(ScheduledJobRunRegistry.ACCOUNT_ANONYMIZATION_JOB).lastRun())
            .isEqualTo(now);
        assertThat(registry.stateFor(ScheduledJobRunRegistry.MEDIA_CLEANUP_JOB).lastOutcome())
            .isEqualTo(ScheduledJobRunRegistry.Outcome.FAILURE);
        assertThat(registry.stateFor(ScheduledJobRunRegistry.RECURRING_FINANCE_JOB).lastOutcome())
            .isEqualTo(ScheduledJobRunRegistry.Outcome.NEVER_RUN);
    }

    @Test
    void unknownJobsCannotPolluteTheContract() {
        ScheduledJobRunRegistry registry = new ScheduledJobRunRegistry();

        assertThatThrownBy(() -> registry.record("UnknownJob",
            ScheduledJobRunRegistry.Outcome.SUCCESS, Instant.now()))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
