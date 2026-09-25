package kh.edu.acleda.campuslink.modules.admin.analytics.cache;

import java.time.Clock;
import java.time.Duration;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** In-process TTL cache dedicated to the read-only admin analytics projections. */
@Configuration(proxyBeanMethods = false)
public class AnalyticsCacheConfig {

    public static final String CACHE_MANAGER_BEAN = "analyticsCacheManager";
    public static final String CACHE_NAME = "adminAnalytics";
    public static final Duration TTL = Duration.ofMinutes(5);

    @Bean(name = CACHE_MANAGER_BEAN)
    public CacheManager analyticsCacheManager() {
        return new AnalyticsCacheManager(CACHE_NAME, TTL, Clock.systemUTC());
    }
}
