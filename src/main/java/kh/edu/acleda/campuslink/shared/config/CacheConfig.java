package kh.edu.acleda.campuslink.shared.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/** Turns on {@code @Cacheable} support. */
@Configuration
@EnableCaching
public class CacheConfig {

    /** Keep the default, non-expiring manager explicit. */
    @Bean(name = "cacheManager")
    @Primary
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
