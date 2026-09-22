package kh.edu.acleda.campuslink.modules.admin.systemhealth;

import java.lang.management.ManagementFactory;
import java.lang.management.RuntimeMXBean;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.LongSupplier;
import java.util.function.Supplier;
import kh.edu.acleda.campuslink.modules.admin.systemhealth.dto.response.AdminSystemHealthResponse;
import kh.edu.acleda.campuslink.modules.media.storage.StorageHealthProbe;
import kh.edu.acleda.campuslink.shared.service.MailDeliveryRegistry;
import kh.edu.acleda.campuslink.shared.service.ScheduledJobRunRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.actuate.health.HealthComponent;
import org.springframework.boot.actuate.health.HealthEndpoint;
import org.springframework.stereotype.Service;

/** Aggregates normalized, non-sensitive subsystem state for the admin portal. */
@Slf4j
@Service
public class AdminSystemHealthService {

    private static final Duration ACTUATOR_CACHE_TTL = Duration.ofSeconds(5);

    private final HealthEndpoint healthEndpoint;
    private final StorageHealthProbe storageHealthProbe;
    private final MailDeliveryRegistry mailDeliveryRegistry;
    private final ScheduledJobRunRegistry scheduledJobRunRegistry;
    private final boolean mailEnabled;
    private final Clock clock;
    private final LongSupplier uptimeSeconds;
    private final Duration actuatorCacheTtl;
    private volatile ActuatorSnapshot actuatorSnapshot;

    @Autowired
    public AdminSystemHealthService(
        HealthEndpoint healthEndpoint,
        StorageHealthProbe storageHealthProbe,
        MailDeliveryRegistry mailDeliveryRegistry,
        ScheduledJobRunRegistry scheduledJobRunRegistry,
        @Value("${app.mail.enabled:true}") boolean mailEnabled) {
        this(healthEndpoint, storageHealthProbe, mailDeliveryRegistry, scheduledJobRunRegistry,
            mailEnabled, Clock.systemUTC(), defaultUptimeSeconds(), ACTUATOR_CACHE_TTL);
    }

    AdminSystemHealthService(
        HealthEndpoint healthEndpoint,
        StorageHealthProbe storageHealthProbe,
        MailDeliveryRegistry mailDeliveryRegistry,
        ScheduledJobRunRegistry scheduledJobRunRegistry,
        boolean mailEnabled,
        Clock clock,
        LongSupplier uptimeSeconds,
        Duration actuatorCacheTtl) {
        this.healthEndpoint = healthEndpoint;
        this.storageHealthProbe = storageHealthProbe;
        this.mailDeliveryRegistry = mailDeliveryRegistry;
        this.scheduledJobRunRegistry = scheduledJobRunRegistry;
        this.mailEnabled = mailEnabled;
        this.clock = clock;
        this.uptimeSeconds = uptimeSeconds;
        this.actuatorCacheTtl = actuatorCacheTtl;
    }

    public AdminSystemHealthResponse getHealth() {
        Instant checkedAt = clock.instant();
        ActuatorSnapshot actuator = actuatorHealth(checkedAt);
        StorageHealthProbe.ProbeResult storage = storageHealthProbe.check();

        MailDeliveryRegistry.Snapshot mailSnapshot = mailDeliveryRegistry.snapshot();
        AdminSystemHealthResponse.MailHealth mail = mailHealth(mailSnapshot);
        List<AdminSystemHealthResponse.ScheduledJobHealth> jobs = scheduledJobRunRegistry.snapshot()
            .stream()
            .map(this::toScheduledJobHealth)
            .toList();

        AdminSystemHealthResponse.ComponentHealth api = component(actuator.apiStatus());
        AdminSystemHealthResponse.ComponentHealth database = component(actuator.databaseStatus());
        AdminSystemHealthResponse.ComponentHealth storageHealth = component(
            storage.status() == StorageHealthProbe.Status.UP
                ? AdminSystemHealthResponse.Status.UP
                : AdminSystemHealthResponse.Status.DOWN);

        return new AdminSystemHealthResponse(
            overallStatus(actuator.apiStatus(), actuator.databaseStatus(), storageHealth.status(), mail, jobs),
            checkedAt,
            Math.max(0L, uptimeSeconds.getAsLong()),
            api,
            database,
            storageHealth,
            mail,
            jobs
        );
    }

    private ActuatorSnapshot actuatorHealth(Instant now) {
        ActuatorSnapshot current = actuatorSnapshot;
        if (current != null && now.isBefore(current.expiresAt())) {
            return current;
        }

        synchronized (this) {
            now = clock.instant();
            current = actuatorSnapshot;
            if (current != null && now.isBefore(current.expiresAt())) {
                return current;
            }

            StatusPair status = new StatusPair(
                apiStatus(),
                databaseStatus());
            ActuatorSnapshot refreshed = new ActuatorSnapshot(
                status.apiStatus(), status.databaseStatus(), now.plus(actuatorCacheTtl));
            actuatorSnapshot = refreshed;
            return refreshed;
        }
    }

    private AdminSystemHealthResponse.Status apiStatus() {
        HealthComponent ping = safeHealth(() -> healthEndpoint.healthForPath("ping"));
        if (ping != null) {
            return normalize(ping);
        }
        // The endpoint itself is alive if no dedicated ping contributor exists. Use the
        // aggregate only as a fallback for unusual Actuator configurations.
        HealthComponent aggregate = safeHealth(healthEndpoint::health);
        return aggregate == null ? AdminSystemHealthResponse.Status.DOWN : normalize(aggregate);
    }

    private AdminSystemHealthResponse.Status databaseStatus() {
        HealthComponent database = safeHealth(() -> healthEndpoint.healthForPath("db"));
        return database == null ? AdminSystemHealthResponse.Status.DOWN : normalize(database);
    }

    private HealthComponent safeHealth(Supplier<HealthComponent> healthSupplier) {
        try {
            return healthSupplier.get();
        } catch (RuntimeException exception) {
            log.warn("Actuator health component unavailable; reporting DOWN");
            return null;
        }
    }

    private AdminSystemHealthResponse.MailHealth mailHealth(MailDeliveryRegistry.Snapshot snapshot) {
        AdminSystemHealthResponse.MailOutcome outcome = AdminSystemHealthResponse.MailOutcome
            .valueOf(snapshot.lastOutcome().name());
        AdminSystemHealthResponse.Status status = !mailEnabled || outcome != AdminSystemHealthResponse.MailOutcome.FAILURE
            ? AdminSystemHealthResponse.Status.UP
            : AdminSystemHealthResponse.Status.DOWN;
        return new AdminSystemHealthResponse.MailHealth(status, mailEnabled, outcome);
    }

    private AdminSystemHealthResponse.ScheduledJobHealth toScheduledJobHealth(
        ScheduledJobRunRegistry.JobRun jobRun) {
        return new AdminSystemHealthResponse.ScheduledJobHealth(
            jobRun.name(),
            jobRun.lastRun(),
            AdminSystemHealthResponse.JobOutcome.valueOf(jobRun.lastOutcome().name()));
    }

    private AdminSystemHealthResponse.Status overallStatus(
        AdminSystemHealthResponse.Status api,
        AdminSystemHealthResponse.Status database,
        AdminSystemHealthResponse.Status storage,
        AdminSystemHealthResponse.MailHealth mail,
        List<AdminSystemHealthResponse.ScheduledJobHealth> jobs) {
        if (api != AdminSystemHealthResponse.Status.UP) {
            return AdminSystemHealthResponse.Status.DOWN;
        }
        boolean jobFailed = jobs.stream()
            .anyMatch(job -> job.lastOutcome() == AdminSystemHealthResponse.JobOutcome.FAILURE);
        if (database != AdminSystemHealthResponse.Status.UP
            || storage != AdminSystemHealthResponse.Status.UP
            || mail.status() != AdminSystemHealthResponse.Status.UP
            || jobFailed) {
            return AdminSystemHealthResponse.Status.DEGRADED;
        }
        return AdminSystemHealthResponse.Status.UP;
    }

    private static AdminSystemHealthResponse.ComponentHealth component(
        AdminSystemHealthResponse.Status status) {
        return new AdminSystemHealthResponse.ComponentHealth(status);
    }

    private static AdminSystemHealthResponse.Status normalize(HealthComponent component) {
        return "UP".equalsIgnoreCase(component.getStatus().getCode())
            ? AdminSystemHealthResponse.Status.UP
            : AdminSystemHealthResponse.Status.DOWN;
    }

    private static LongSupplier defaultUptimeSeconds() {
        RuntimeMXBean runtime = ManagementFactory.getRuntimeMXBean();
        return () -> runtime.getUptime() / 1000L;
    }

    private record StatusPair(
        AdminSystemHealthResponse.Status apiStatus,
        AdminSystemHealthResponse.Status databaseStatus) {
    }

    private record ActuatorSnapshot(
        AdminSystemHealthResponse.Status apiStatus,
        AdminSystemHealthResponse.Status databaseStatus,
        Instant expiresAt) {
    }
}
