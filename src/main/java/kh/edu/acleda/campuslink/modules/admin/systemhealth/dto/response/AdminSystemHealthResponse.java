package kh.edu.acleda.campuslink.modules.admin.systemhealth.dto.response;

import java.time.Instant;
import java.util.List;

/** Public-safe, normalized health data for the admin portal. */
public record AdminSystemHealthResponse(
    Status status,
    Instant checkedAt,
    long uptimeSeconds,
    ComponentHealth api,
    ComponentHealth database,
    ComponentHealth storage,
    MailHealth mail,
    List<ScheduledJobHealth> scheduledJobs
) {

    public AdminSystemHealthResponse {
        scheduledJobs = scheduledJobs == null ? List.of() : List.copyOf(scheduledJobs);
    }

    public enum Status {
        UP,
        DEGRADED,
        DOWN
    }

    public record ComponentHealth(Status status) {
    }

    public record MailHealth(Status status, boolean enabled, MailOutcome lastOutcome) {
    }

    public record ScheduledJobHealth(String name, Instant lastRun, JobOutcome lastOutcome) {
    }

    public enum MailOutcome {
        SUCCESS,
        FAILURE,
        NEVER_RUN
    }

    public enum JobOutcome {
        SUCCESS,
        FAILURE,
        NEVER_RUN
    }
}
