package kh.edu.acleda.campuslink.modules.media;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Tuning for the daily media sweeps, bound from {@code app.media.cleanup.*}.
 *
 * <p>Every value is externalised so an operator can retime or resize a run
 * without a redeploy — useful when a backlog needs draining faster, or when a
 * sweep needs to be slowed down because object deletes are rate-limited.
 */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.media.cleanup")
public class MediaCleanupProperties {

    /**
     * How long a PENDING row may sit before it is treated as abandoned. Must stay
     * comfortably longer than the presign expiry so a slow upload is never reaped
     * out from under a client that is still working.
     */
    private Duration orphanAge = Duration.ofHours(24);

    /**
     * How long a soft-deleted object stays recoverable before its bytes are
     * removed. Matches the account-deletion window by default.
     */
    private Duration retention = Duration.ofDays(30);

    /**
     * Rows handled per sweep per run. Bounds both memory and transaction size; a
     * backlog larger than this simply continues on the following run.
     */
    private int batchSize = 500;
}
