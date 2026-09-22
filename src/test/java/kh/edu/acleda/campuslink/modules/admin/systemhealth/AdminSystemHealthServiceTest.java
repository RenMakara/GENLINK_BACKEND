package kh.edu.acleda.campuslink.modules.admin.systemhealth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import kh.edu.acleda.campuslink.modules.admin.systemhealth.dto.response.AdminSystemHealthResponse;
import kh.edu.acleda.campuslink.modules.media.storage.StorageHealthProbe;
import kh.edu.acleda.campuslink.shared.service.MailDeliveryRegistry;
import kh.edu.acleda.campuslink.shared.service.ScheduledJobRunRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthEndpoint;

@ExtendWith(MockitoExtension.class)
class AdminSystemHealthServiceTest {

    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

    @Mock
    private HealthEndpoint healthEndpoint;

    @Mock
    private StorageHealthProbe storageHealthProbe;

    private MailDeliveryRegistry mailDeliveryRegistry;
    private ScheduledJobRunRegistry scheduledJobRunRegistry;
    private AdminSystemHealthService service;

    @BeforeEach
    void setUp() {
        mailDeliveryRegistry = new MailDeliveryRegistry();
        scheduledJobRunRegistry = new ScheduledJobRunRegistry();
        service = new AdminSystemHealthService(
            healthEndpoint,
            storageHealthProbe,
            mailDeliveryRegistry,
            scheduledJobRunRegistry,
            true,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> 12345L,
            Duration.ofMinutes(1));
        when(storageHealthProbe.check()).thenReturn(
            new StorageHealthProbe.ProbeResult(StorageHealthProbe.Status.UP, NOW));
        when(healthEndpoint.healthForPath("ping")).thenReturn(Health.up().build());
        when(healthEndpoint.healthForPath("db")).thenReturn(Health.up().build());
    }

    @Test
    void allComponentsHealthyProducesUpWithStableJobRows() {
        AdminSystemHealthResponse response = service.getHealth();

        assertThat(response.status()).isEqualTo(AdminSystemHealthResponse.Status.UP);
        assertThat(response.checkedAt()).isEqualTo(NOW);
        assertThat(response.uptimeSeconds()).isEqualTo(12345L);
        assertThat(response.api().status()).isEqualTo(AdminSystemHealthResponse.Status.UP);
        assertThat(response.database().status()).isEqualTo(AdminSystemHealthResponse.Status.UP);
        assertThat(response.storage().status()).isEqualTo(AdminSystemHealthResponse.Status.UP);
        assertThat(response.mail().lastOutcome()).isEqualTo(AdminSystemHealthResponse.MailOutcome.NEVER_RUN);
        assertThat(response.scheduledJobs()).hasSize(3);
        assertThat(response.scheduledJobs()).allSatisfy(job -> {
            assertThat(job.lastRun()).isNull();
            assertThat(job.lastOutcome()).isEqualTo(AdminSystemHealthResponse.JobOutcome.NEVER_RUN);
        });
    }

    @Test
    void databaseFailureIsDownButOverallHealthIsDegraded() {
        when(healthEndpoint.healthForPath("db")).thenReturn(Health.down().build());

        AdminSystemHealthResponse response = service.getHealth();

        assertThat(response.database().status()).isEqualTo(AdminSystemHealthResponse.Status.DOWN);
        assertThat(response.api().status()).isEqualTo(AdminSystemHealthResponse.Status.UP);
        assertThat(response.status()).isEqualTo(AdminSystemHealthResponse.Status.DEGRADED);
    }

    @Test
    void storageFailureIsDownButDoesNotBecomeHttpFailure() {
        when(storageHealthProbe.check()).thenReturn(
            new StorageHealthProbe.ProbeResult(StorageHealthProbe.Status.DOWN, NOW));

        AdminSystemHealthResponse response = service.getHealth();

        assertThat(response.storage().status()).isEqualTo(AdminSystemHealthResponse.Status.DOWN);
        assertThat(response.status()).isEqualTo(AdminSystemHealthResponse.Status.DEGRADED);
    }

    @Test
    void disabledMailIsExplicitlyHealthyAndNeverRun() {
        service = new AdminSystemHealthService(
            healthEndpoint,
            storageHealthProbe,
            mailDeliveryRegistry,
            scheduledJobRunRegistry,
            false,
            Clock.fixed(NOW, ZoneOffset.UTC),
            () -> 1L,
            Duration.ofMinutes(1));

        AdminSystemHealthResponse response = service.getHealth();

        assertThat(response.mail().enabled()).isFalse();
        assertThat(response.mail().status()).isEqualTo(AdminSystemHealthResponse.Status.UP);
        assertThat(response.mail().lastOutcome()).isEqualTo(AdminSystemHealthResponse.MailOutcome.NEVER_RUN);
        assertThat(response.status()).isEqualTo(AdminSystemHealthResponse.Status.UP);
    }

    @Test
    void mailFailureAndJobFailureDegradeOverallStatus() {
        mailDeliveryRegistry.recordFailure();
        scheduledJobRunRegistry.recordFailure(ScheduledJobRunRegistry.MEDIA_CLEANUP_JOB);

        AdminSystemHealthResponse response = service.getHealth();

        assertThat(response.mail().status()).isEqualTo(AdminSystemHealthResponse.Status.DOWN);
        assertThat(response.mail().lastOutcome()).isEqualTo(AdminSystemHealthResponse.MailOutcome.FAILURE);
        assertThat(response.scheduledJobs())
            .filteredOn(job -> job.name().equals(ScheduledJobRunRegistry.MEDIA_CLEANUP_JOB))
            .extracting(AdminSystemHealthResponse.ScheduledJobHealth::lastOutcome)
            .containsExactly(AdminSystemHealthResponse.JobOutcome.FAILURE);
        assertThat(response.status()).isEqualTo(AdminSystemHealthResponse.Status.DEGRADED);
    }

    @Test
    void actuatorExceptionIsNormalizedWithoutLeakingDetails() throws Exception {
        when(healthEndpoint.healthForPath("db"))
            .thenThrow(new IllegalStateException("jdbc://secret-password"));

        AdminSystemHealthResponse response = service.getHealth();
        String json = new com.fasterxml.jackson.databind.ObjectMapper()
                .findAndRegisterModules()
                .writeValueAsString(response);

        assertThat(response.database().status()).isEqualTo(AdminSystemHealthResponse.Status.DOWN);
        assertThat(response.status()).isEqualTo(AdminSystemHealthResponse.Status.DEGRADED);
        assertThat(json).doesNotContain("jdbc", "secret-password", "password", "endpoint");
    }

    @Test
    void apiFailureProducesDownOverallStatus() {
        when(healthEndpoint.healthForPath("ping")).thenReturn(Health.down().build());

        AdminSystemHealthResponse response = service.getHealth();

        assertThat(response.api().status()).isEqualTo(AdminSystemHealthResponse.Status.DOWN);
        assertThat(response.status()).isEqualTo(AdminSystemHealthResponse.Status.DOWN);
    }
}
