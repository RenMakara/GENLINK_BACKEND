package kh.edu.acleda.campuslink.shared.config;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Turns on {@code @Scheduled} support.
 *
 * <p>Kept in its own class rather than on the application class so it can be switched
 * off with {@code app.scheduling.enabled=false} — useful when running more than one
 * instance, where a daily job should not fire once per replica.
 */
@Configuration
@EnableScheduling
@ConditionalOnProperty(name = "app.scheduling.enabled", havingValue = "true", matchIfMissing = true)
public class SchedulingConfig {
}
