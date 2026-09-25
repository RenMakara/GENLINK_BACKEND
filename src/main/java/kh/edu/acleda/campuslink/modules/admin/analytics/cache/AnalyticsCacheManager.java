package kh.edu.acleda.campuslink.modules.admin.analytics.cache;

import java.time.Clock;
import java.time.Duration;
import java.util.Collection;
import java.util.List;
import org.springframework.cache.Cache;
import org.springframework.cache.CacheManager;

/** Cache manager exposing the single bounded-purpose analytics cache. */
public final class AnalyticsCacheManager implements CacheManager {

    private final String cacheName;
    private final Cache cache;

    public AnalyticsCacheManager(String cacheName, Duration ttl, Clock clock) {
        this.cacheName = cacheName;
        this.cache = new TtlCache(cacheName, ttl, clock);
    }

    @Override
    public Cache getCache(String name) {
        return cacheName.equals(name) ? cache : null;
    }

    @Override
    public Collection<String> getCacheNames() {
        return List.of(cacheName);
    }
}
