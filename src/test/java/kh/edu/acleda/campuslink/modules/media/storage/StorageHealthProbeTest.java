package kh.edu.acleda.campuslink.modules.media.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class StorageHealthProbeTest {

    private static final Instant NOW = Instant.parse("2026-09-23T00:00:00Z");

    @Mock
    private StorageService storage;

    private Clock clock;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(NOW, ZoneOffset.UTC);
    }

    @Test
    void aHealthyStoreIsReportedUpAndRepeatedReadsUseTheShortCache() {
        doNothing().when(storage).checkAvailability(any());
        StorageHealthProbe probe = new StorageHealthProbe(
            storage, clock, Duration.ofSeconds(2), Duration.ofSeconds(10));

        assertThat(probe.check().status()).isEqualTo(StorageHealthProbe.Status.UP);
        assertThat(probe.check().status()).isEqualTo(StorageHealthProbe.Status.UP);

        verify(storage, times(1)).checkAvailability(any());
    }

    @Test
    void providerFailureIsNormalizedToDown() {
        doThrow(new IllegalStateException("secret endpoint details"))
            .when(storage).checkAvailability(any());
        StorageHealthProbe probe = new StorageHealthProbe(
            storage, clock, Duration.ofSeconds(2), Duration.ofSeconds(10));

        assertThat(probe.check().status()).isEqualTo(StorageHealthProbe.Status.DOWN);
    }

    @Test
    void aSlowProviderCannotHoldTheHealthProbeOpen() {
        doAnswer(invocation -> {
            try {
                Thread.sleep(250);
            } catch (InterruptedException exception) {
                Thread.currentThread().interrupt();
            }
            return null;
        }).when(storage).checkAvailability(any());
        StorageHealthProbe probe = new StorageHealthProbe(
            storage, clock, Duration.ofMillis(40), Duration.ofSeconds(10));

        long started = System.nanoTime();
        StorageHealthProbe.ProbeResult result = probe.check();
        long elapsedMs = (System.nanoTime() - started) / 1_000_000;

        assertThat(result.status()).isEqualTo(StorageHealthProbe.Status.DOWN);
        assertThat(elapsedMs).isLessThan(500L);
    }
}
