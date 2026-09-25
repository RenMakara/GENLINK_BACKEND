package kh.edu.acleda.campuslink.shared.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;

/**
 * Turns on {@code @Cacheable} support.
 *
 * <p>{@code spring-boot-starter-cache} was already a dependency but nothing enabled it, so
 * every {@code @Cacheable} in the codebase was inert. Admin permission resolution is the first
 * thing that genuinely needs it: it runs on every authenticated request, mobile included.
 *
 * <p>The default cache manager is an in-process {@code ConcurrentMapCacheManager} with
 * <strong>no expiry</strong>. That is deliberate rather than an oversight for permission
 * caches:
 *
 * <ul>
 *   <li>The backend runs a single instance (enforced by {@code container_name} in
 *       {@code docker-compose.yml}), so an eviction is exact - there is no second node still
 *       serving a stale entry.
 *   <li>With eviction exact, a TTL would only be a backstop against a <em>missed</em> eviction
 *       path, and that is better covered by tests than by a dependency.
 * </ul>
 *
 * <p>The consequence: <strong>eviction is the correctness mechanism.</strong> Any write that
 * changes what an admin may do must evict, or the change will not take effect until restart.
 * If the deployment ever grows a second replica, this and the in-memory rate limiter both need
 * moving to Redis together.
 */
@Configuration
@EnableCaching
public class CacheConfig {

    /**
     * Keep the default, non-expiring manager explicit so feature-specific managers can coexist
     * without changing the semantics of permission caches.
     */
    @Bean(name = "cacheManager")
    @Primary
    public CacheManager cacheManager() {
        return new ConcurrentMapCacheManager();
    }
}
