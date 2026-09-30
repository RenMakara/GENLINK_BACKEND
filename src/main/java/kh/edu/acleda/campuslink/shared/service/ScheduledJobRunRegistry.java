package kh.edu.acleda.campuslink.shared.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/** Minimal process-local registry for the scheduled jobs surfaced by the admin health panel. */
@Component
public class ScheduledJobRunRegistry {

    public static final String ACCOUNT_ANONYMIZATION_JOB = "AccountAnonymizationJob";
    public static final String RECURRING_FINANCE_JOB = "RecurringFinanceJob";
    public static final String MEDIA_CLEANUP_JOB = "MediaCleanupJob";

    private static final List<String> TRACKED_JOB_NAMES = List.of(
        ACCOUNT_ANONYMIZATION_JOB,
        RECURRING_FINANCE_JOB,
        MEDIA_CLEANUP_JOB
    );

    private final Map<String, JobRun> runs = new ConcurrentHashMap<>();

    public ScheduledJobRunRegistry() {
        TRACKED_JOB_NAMES.forEach(name -> runs.put(name, new JobRun(name, null, Outcome.NEVER_RUN)));
    }

    public void recordSuccess(String jobName) {
        record(jobName, Outcome.SUCCESS, Instant.now());
    }

    public void recordFailure(String jobName) {
        record(jobName, Outcome.FAILURE, Instant.now());
    }

    public List<JobRun> snapshot() {
        return TRACKED_JOB_NAMES.stream().map(runs::get).toList();
    }

    JobRun stateFor(String jobName) {
        return runs.get(jobName);
    }

    void record(String jobName, Outcome outcome, Instant recordedAt) {
        if (!runs.containsKey(jobName)) {
            throw new IllegalArgumentException("Unknown scheduled job: " + jobName);
        }
        if (outcome == null || outcome == Outcome.NEVER_RUN) {
            throw new IllegalArgumentException("A completed job needs a SUCCESS or FAILURE outcome");
        }
        runs.put(jobName, new JobRun(jobName, recordedAt, outcome));
    }

    public enum Outcome {
        SUCCESS,
        FAILURE,
        NEVER_RUN
    }

    public record JobRun(String name, Instant lastRun, Outcome lastOutcome) {
    }
}
