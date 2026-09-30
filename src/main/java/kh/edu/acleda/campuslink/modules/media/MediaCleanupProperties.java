package kh.edu.acleda.campuslink.modules.media;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Tuning for the daily media sweeps, bound from {@code app.media.cleanup.*}. */
@Getter
@Setter
@ConfigurationProperties(prefix = "app.media.cleanup")
public class MediaCleanupProperties {

    /** How long a PENDING row may sit before it is treated as abandoned. */
    private Duration orphanAge = Duration.ofHours(24);

    /** How long a soft-deleted object stays recoverable before its bytes are removed. */
    private Duration retention = Duration.ofDays(30);

    /** Rows handled per sweep per run. */
    private int batchSize = 500;
}
