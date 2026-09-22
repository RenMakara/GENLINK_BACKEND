package kh.edu.acleda.campuslink.modules.media.storage;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * Bounded, cached availability probe for the configured object store.
 *
 * <p>The SDK request has its own two-second timeout, while the future below keeps a provider
 * implementation or test double from holding the admin request open indefinitely. Results are
 * cached for ten seconds so a dashboard refresh does not create an S3 request every time.
 */
@Slf4j
@Component
public class StorageHealthProbe {

    public static final Duration PROBE_TIMEOUT = Duration.ofSeconds(2);
    public static final Duration CACHE_TTL = Duration.ofSeconds(10);

    private final StorageService storage;
    private final Clock clock;
    private final Duration timeout;
    private final Duration cacheTtl;
    private volatile CachedResult cached;

    @Autowired
    public StorageHealthProbe(StorageService storage) {
        this(storage, Clock.systemUTC(), PROBE_TIMEOUT, CACHE_TTL);
    }

    StorageHealthProbe(StorageService storage, Clock clock, Duration timeout, Duration cacheTtl) {
        if (timeout == null || timeout.isZero() || timeout.isNegative()) {
            throw new IllegalArgumentException("Storage health timeout must be positive");
        }
        if (cacheTtl == null || cacheTtl.isZero() || cacheTtl.isNegative()) {
            throw new IllegalArgumentException("Storage health cache TTL must be positive");
        }
        this.storage = storage;
        this.clock = clock;
        this.timeout = timeout;
        this.cacheTtl = cacheTtl;
    }

    public ProbeResult check() {
        Instant now = clock.instant();
        CachedResult current = cached;
        if (current != null && now.isBefore(current.expiresAt())) {
            return current.result();
        }

        synchronized (this) {
            now = clock.instant();
            current = cached;
            if (current != null && now.isBefore(current.expiresAt())) {
                return current.result();
            }

            ProbeResult result = performProbe(now);
            cached = new CachedResult(result, now.plus(cacheTtl));
            return result;
        }
    }

    private ProbeResult performProbe(Instant checkedAt) {
        Future<?> operation = CompletableFuture.runAsync(
            () -> storage.checkAvailability(timeout));
        try {
            operation.get(Math.max(1L, timeout.toMillis()), TimeUnit.MILLISECONDS);
            return new ProbeResult(Status.UP, checkedAt);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            operation.cancel(true);
            log.warn("Object storage health probe was interrupted; reporting DOWN");
        } catch (TimeoutException | ExecutionException | RuntimeException exception) {
            operation.cancel(true);
            log.warn("Object storage health probe did not complete; reporting DOWN");
        }
        return new ProbeResult(Status.DOWN, checkedAt);
    }

    public enum Status {
        UP,
        DOWN
    }

    public record ProbeResult(Status status, Instant checkedAt) {
    }

    private record CachedResult(ProbeResult result, Instant expiresAt) {
    }
}
