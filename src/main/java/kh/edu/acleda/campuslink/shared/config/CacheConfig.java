package kh.edu.acleda.campuslink.shared.config;

import org.springframework.cache.annotation.EnableCaching;
import org.springframework.context.annotation.Configuration;

/**
 * Turns on {@code @Cacheable} support.
 *
 * <p>{@code spring-boot-starter-cache} was already a dependency but nothing enabled it, so
 * every {@code @Cacheable} in the codebase was inert. Admin permission resolution is the first
 * thing that genuinely needs it: it runs on every authenticated request, mobile included.
 *
 * <p>No cache provider is configured, so Spring Boot uses its {@code ConcurrentMapCacheManager}
 * - an in-process map with <strong>no expiry</strong>. That is deliberate rather than an
 * oversight:
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
}
